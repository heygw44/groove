package com.groove.global.lock;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;

import com.zaxxer.hikari.HikariDataSource;

/**
 * MySQL named lock(GET_LOCK/RELEASE_LOCK)을 락 이름 단위로 감싸는 범용 유틸리티.
 *
 * <p>MySQL named lock 은 커넥션(세션) 스코프다. 반드시 커넥션 풀 밖에서 연 전용 커넥션으로 잡아야 한다 -
 * 풀에서 빌리면 {@code close()} 가 세션을 끝내지 않고 풀에 반납할 뿐이라, RELEASE_LOCK 호출이 어떤 이유로든
 * 한 번이라도 어긋나면(재진입 카운터가 남거나, 예외로 스킵되거나) 그 세션이 락을 쥔 채 풀을 돌아다니게 되고
 * 재기동 전까지 아무도 알아채지 못한 채 배치가 전부 막힌다. 전용 커넥션은 {@code close()} 가 곧 세션 종료라
 * RELEASE_LOCK 이 어떻게 되든 락이 확실히 풀린다.</p>
 *
 * <p>획득 실패 시 {@code SELECT IS_USED_LOCK(?)} 로 보유 세션의 connection id 를 같이 조회해 락 이름별로
 * "직전 실패 때의 보유자 id" 와 비교한다. 정상 실행은 매번 새 전용 커넥션을 열므로 보유자 id 가 매번 바뀌지만,
 * 앱을 여러 대 띄우면 같은 상대에게 매 주기 지는 경우가 생기고 그 자체는 정상이다. 반대로 커넥션 풀에 갇힌
 * 누수 세션은 보유자 id 가 고정된다. 그래서 보유자 id 가 직전 실패와 같은 채로 3회 연속이면 "누수 가능성"
 * error 를 남기고(이후 20회마다 반복), 보유자가 바뀌었거나 NULL(락이 막 풀림)이면 카운터를 리셋하고 debug 로만
 * 남긴다. 락 이름별로 상태를 따로 관리해 이 유틸을 공유하는 서로 다른 배치끼리 섞이지 않는다. 로깅은 호출한 쪽의
 * {@link Logger} 로 남겨, 로그가 실제 락을 쓰는 도메인 클래스 이름으로 찍히게 한다.</p>
 */
public class NamedLock {

	private static final int SAME_HOLDER_ALERT_THRESHOLD = 3;
	private static final int SAME_HOLDER_ALERT_INTERVAL = 20;

	private final LockConnectionProvider connectionProvider;
	private final Logger log;
	private final Map<String, FailureTracker> failureTrackers = new ConcurrentHashMap<>();

	public NamedLock(DataSource dataSource, Logger log) {
		this(toDedicatedConnectionProvider(dataSource), log);
	}

	public NamedLock(LockConnectionProvider connectionProvider, Logger log) {
		this.connectionProvider = connectionProvider;
		this.log = log;
	}

	private static LockConnectionProvider toDedicatedConnectionProvider(DataSource dataSource) {
		if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
			throw new IllegalStateException("NamedLock 은 HikariDataSource 기반 DataSource 만 지원한다");
		}
		String url = hikariDataSource.getJdbcUrl();
		String username = hikariDataSource.getUsername();
		String password = hikariDataSource.getPassword();
		return () -> DriverManager.getConnection(url, username, password);
	}

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(String lockName, Runnable task) {
		try (Connection lockConnection = connectionProvider.open()) {
			if (!tryGetLock(lockConnection, lockName)) {
				noteAcquireFailure(lockConnection, lockName);
				return false;
			}
			failureTrackers.remove(lockName);
			try {
				task.run();
				return true;
			} finally {
				releaseLock(lockConnection, lockName);
			}
		} catch (SQLException e) {
			log.warn("named lock 커넥션 처리 중 오류가 발생했다 lockName={}", lockName, e);
			return false;
		}
	}

	private boolean tryGetLock(Connection connection, String lockName) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
			statement.setString(1, lockName);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1) == 1;
			}
		}
	}

	private void releaseLock(Connection connection, String lockName) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
			statement.setString(1, lockName);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				int released = resultSet.getInt(1);
				if (resultSet.wasNull() || released != 1) {
					log.error("named lock 해제가 비정상 반환값을 받았다 lockName={} released={} (전용 커넥션을 닫아 세션 단위로는 정리된다)",
							lockName, resultSet.wasNull() ? "NULL" : released);
				}
			}
		}
	}

	/** 락을 쥔 세션의 connection id 를 조회한다. 아무도 쥐고 있지 않으면 null. */
	private Long currentHolderConnectionId(Connection connection, String lockName) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT IS_USED_LOCK(?)")) {
			statement.setString(1, lockName);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				long holderConnectionId = resultSet.getLong(1);
				return resultSet.wasNull() ? null : holderConnectionId;
			}
		}
	}

	/**
	 * 보유자 id 가 직전 실패와 같은 채로 {@value #SAME_HOLDER_ALERT_THRESHOLD} 회 연속이면 error 로 드러낸다(이후
	 * {@value #SAME_HOLDER_ALERT_INTERVAL} 회마다 반복). 보유자가 바뀌었거나 NULL 이면 카운터를 리셋한다.
	 */
	private void noteAcquireFailure(Connection connection, String lockName) throws SQLException {
		Long holderConnectionId = currentHolderConnectionId(connection, lockName);
		FailureTracker tracker = failureTrackers.computeIfAbsent(lockName, key -> new FailureTracker());
		synchronized (tracker) {
			if (holderConnectionId != null && Objects.equals(holderConnectionId, tracker.holderConnectionId)) {
				tracker.count++;
			} else {
				tracker.holderConnectionId = holderConnectionId;
				tracker.count = 1;
			}
			boolean shouldAlert = tracker.count == SAME_HOLDER_ALERT_THRESHOLD
					|| (tracker.count > SAME_HOLDER_ALERT_THRESHOLD && tracker.count % SAME_HOLDER_ALERT_INTERVAL == 0);
			if (shouldAlert) {
				log.error("같은 세션이 named lock 을 {}회 연속 쥐고 있다 lockName={} holderConnectionId={} - 누수 가능성이 있다",
						tracker.count, lockName, holderConnectionId);
			} else {
				log.debug("named lock 획득 실패 lockName={} holderConnectionId={} count={}", lockName, holderConnectionId,
						tracker.count);
			}
		}
	}

	private static final class FailureTracker {

		private Long holderConnectionId;
		private int count;
	}

	@FunctionalInterface
	public interface LockConnectionProvider {

		Connection open() throws SQLException;
	}
}
