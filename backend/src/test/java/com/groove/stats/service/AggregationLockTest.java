package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AggregationLockTest {

	private DataSource dataSource;
	private Connection connection;
	private AggregationLock aggregationLock;

	@BeforeEach
	void setUp() throws SQLException {
		dataSource = mock(DataSource.class);
		connection = mock(Connection.class);
		given(dataSource.getConnection()).willReturn(connection);
		aggregationLock = new AggregationLock(dataSource);
	}

	private void stubGetLock(int result) throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(connection.prepareStatement(eq("SELECT GET_LOCK(?, 0)"))).willReturn(statement);
		given(statement.executeQuery()).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getInt(1)).willReturn(result);

		if (result == 1) {
			given(connection.prepareStatement(eq("SELECT RELEASE_LOCK(?)")))
					.willReturn(mock(PreparedStatement.class));
		}
	}

	@Nested
	@DisplayName("runExclusively()")
	class RunExclusively {

		@Test
		@DisplayName("락 획득에 성공하면 task 를 실행하고 true 를 반환한다")
		void runsTaskWhenLockAcquired() throws SQLException {
			// given
			stubGetLock(1);
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = aggregationLock.runExclusively(task);

			// then
			assertThat(acquired).isTrue();
			verify(task).run();
		}

		@Test
		@DisplayName("락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다")
		void doesNotRunTaskWhenLockNotAcquired() throws SQLException {
			// given
			stubGetLock(0);
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = aggregationLock.runExclusively(task);

			// then
			assertThat(acquired).isFalse();
			verify(task, never()).run();
		}

		@Test
		@DisplayName("커넥션 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다")
		void doesNotRunTaskWhenConnectionFails() throws SQLException {
			// given
			given(dataSource.getConnection()).willThrow(new SQLException("connection refused"));
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = aggregationLock.runExclusively(task);

			// then
			assertThat(acquired).isFalse();
			verify(task, never()).run();
		}
	}
}
