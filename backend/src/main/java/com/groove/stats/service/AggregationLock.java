package com.groove.stats.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 야간 재집계·15분 증분·관리자 수동 백필의 동시 실행을 막는 MySQL named lock 래퍼.
 * 1순위 방어는 집계 자체의 멱등성이고, 이 락은 두 실행이 같은 날짜를 동시에 써서 생기는 불필요한 경합만 막는다.
 *
 * <p>MySQL named lock 은 커넥션(세션) 스코프다. 반드시 커넥션 풀 밖에서 연 전용 커넥션으로 잡아야 한다 -
 * 풀에서 빌리면 {@code close()} 가 세션을 끝내지 않고 풀에 반납할 뿐이라, RELEASE_LOCK 호출이 어떤 이유로든
 * 한 번이라도 어긋나면(재진입 카운터가 남거나, 예외로 스킵되거나) 그 세션이 락을 쥔 채 풀을 돌아다니게 되고
 * 재기동 전까지 아무도 알아채지 못한 채 배치가 전부 막힌다. 전용 커넥션은 {@code close()} 가 곧 세션 종료라
 * RELEASE_LOCK 이 어떻게 되든 락이 확실히 풀린다.</p>
 */
@Slf4j
@Component
public class AggregationLock {

	static final String LOCK_NAME = "groove:sales-agg";

	private static final int CONSECUTIVE_DENIED_ALERT_THRESHOLD = 3;
	private static final int CONSECUTIVE_DENIED_ALERT_INTERVAL = 20;

	private final LockConnectionProvider connectionProvider;
	private final AtomicInteger consecutiveDenied = new AtomicInteger();

	@Autowired
	public AggregationLock(DataSource dataSource) {
		this(toDedicatedConnectionProvider(dataSource));
	}

	AggregationLock(LockConnectionProvider connectionProvider) {
		this.connectionProvider = connectionProvider;
	}

	private static LockConnectionProvider toDedicatedConnectionProvider(DataSource dataSource) {
		if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
			throw new IllegalStateException("AggregationLock 은 HikariDataSource 기반 DataSource 만 지원한다");
		}
		String url = hikariDataSource.getJdbcUrl();
		String username = hikariDataSource.getUsername();
		String password = hikariDataSource.getPassword();
		return () -> DriverManager.getConnection(url, username, password);
	}

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(Runnable task) {
		try (Connection lockConnection = connectionProvider.open()) {
			if (!tryGetLock(lockConnection)) {
				noteAcquireFailure();
				return false;
			}
			consecutiveDenied.set(0);
			try {
				task.run();
				return true;
			} finally {
				releaseLock(lockConnection);
			}
		} catch (SQLException e) {
			log.warn("집계 락 커넥션 처리 중 오류가 발생했다", e);
			return false;
		}
	}

	private boolean tryGetLock(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
			statement.setString(1, LOCK_NAME);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1) == 1;
			}
		}
	}

	private void releaseLock(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
			statement.setString(1, LOCK_NAME);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				int released = resultSet.getInt(1);
				if (resultSet.wasNull() || released != 1) {
					log.error("집계 락 해제가 비정상 반환값을 받았다 released={} (전용 커넥션을 닫아 세션 단위로는 정리된다)",
							resultSet.wasNull() ? "NULL" : released);
				}
			}
		}
	}

	/** 락 획득이 연속으로 계속 실패하면(=배치가 오래 막혀 있으면) 조용한 info 대신 error 로 드러낸다. */
	private void noteAcquireFailure() {
		int count = consecutiveDenied.incrementAndGet();
		boolean shouldAlert = count == CONSECUTIVE_DENIED_ALERT_THRESHOLD
				|| (count > CONSECUTIVE_DENIED_ALERT_THRESHOLD && count % CONSECUTIVE_DENIED_ALERT_INTERVAL == 0);
		if (shouldAlert) {
			log.error("집계 락 획득이 {}회 연속 실패했다 - 배치가 오랫동안 막혀 있을 수 있다", count);
		}
	}

	@FunctionalInterface
	interface LockConnectionProvider {

		Connection open() throws SQLException;
	}
}
