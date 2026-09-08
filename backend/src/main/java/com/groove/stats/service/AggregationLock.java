package com.groove.stats.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 야간 재집계·15분 증분·관리자 수동 백필의 동시 실행을 막는 MySQL named lock 래퍼.
 * 1순위 방어는 집계 자체의 멱등성이고, 이 락은 두 실행이 같은 날짜를 동시에 써서 생기는 불필요한 경합만 막는다.
 *
 * <p>MySQL named lock 은 커넥션 스코프다. JPA 트랜잭션이 매번 커넥션 풀에서 새 커넥션을 받으므로, 락 전용
 * 커넥션을 {@link DataSource#getConnection()} 으로 직접 열어 task 실행이 끝날 때까지 붙잡고 있어야 한다.
 * 앱이 죽어도 커넥션이 끊기며 락이 자동 해제된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationLock {

	static final String LOCK_NAME = "groove:sales-agg";

	private final DataSource dataSource;

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(Runnable task) {
		try (Connection lockConnection = dataSource.getConnection()) {
			if (!tryGetLock(lockConnection)) {
				return false;
			}
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
			statement.executeQuery();
		}
	}
}
