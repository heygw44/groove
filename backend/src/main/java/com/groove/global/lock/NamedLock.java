package com.groove.global.lock;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <p>연속 획득 실패 카운터는 락 이름별로 따로 관리한다 - 이 유틸을 공유하는 서로 다른 배치가 각자의 락 이름으로
 * 호출해도 한쪽의 실패가 다른 쪽 카운터에 섞이지 않는다. 로깅은 호출한 쪽의 {@link Logger} 로 남겨, 로그가
 * 실제 락을 쓰는 도메인 클래스 이름으로 찍히게 한다.</p>
 */
public class NamedLock {

	private static final int CONSECUTIVE_DENIED_ALERT_THRESHOLD = 3;
	private static final int CONSECUTIVE_DENIED_ALERT_INTERVAL = 20;

	private final LockConnectionProvider connectionProvider;
	private final Logger log;
	private final Map<String, AtomicInteger> consecutiveDenied = new ConcurrentHashMap<>();

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
				noteAcquireFailure(lockName);
				return false;
			}
			consecutiveDenied.computeIfAbsent(lockName, key -> new AtomicInteger()).set(0);
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

	/** 락 획득이 연속으로 계속 실패하면(=배치가 오래 막혀 있으면) 조용한 info 대신 error 로 드러낸다. */
	private void noteAcquireFailure(String lockName) {
		int count = consecutiveDenied.computeIfAbsent(lockName, key -> new AtomicInteger()).incrementAndGet();
		boolean shouldAlert = count == CONSECUTIVE_DENIED_ALERT_THRESHOLD
				|| (count > CONSECUTIVE_DENIED_ALERT_THRESHOLD && count % CONSECUTIVE_DENIED_ALERT_INTERVAL == 0);
		if (shouldAlert) {
			log.error("named lock 획득이 {}회 연속 실패했다 lockName={} - 배치가 오랫동안 막혀 있을 수 있다", count, lockName);
		}
	}

	@FunctionalInterface
	public interface LockConnectionProvider {

		Connection open() throws SQLException;
	}
}
