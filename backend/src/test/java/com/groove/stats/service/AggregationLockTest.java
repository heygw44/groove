package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AggregationLockTest {

	private Connection connection;
	private AggregationLock.LockConnectionProvider connectionProvider;
	private AggregationLock aggregationLock;
	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUp() throws SQLException {
		connection = mock(Connection.class);
		connectionProvider = mock(AggregationLock.LockConnectionProvider.class);
		given(connectionProvider.open()).willReturn(connection);
		aggregationLock = new AggregationLock(connectionProvider);

		logAppender = new ListAppender<>();
		logAppender.start();
		((Logger) LoggerFactory.getLogger(AggregationLock.class)).addAppender(logAppender);
	}

	@AfterEach
	void tearDown() {
		((Logger) LoggerFactory.getLogger(AggregationLock.class)).detachAppender(logAppender);
	}

	private PreparedStatement stubGetLock(int result) throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(connection.prepareStatement(eq("SELECT GET_LOCK(?, 0)"))).willReturn(statement);
		given(statement.executeQuery()).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getInt(1)).willReturn(result);
		return statement;
	}

	private void stubReleaseLock(Integer result) throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(connection.prepareStatement(eq("SELECT RELEASE_LOCK(?)"))).willReturn(statement);
		given(statement.executeQuery()).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getInt(1)).willReturn(result == null ? 0 : result);
		given(resultSet.wasNull()).willReturn(result == null);
	}

	private long errorLogCount() {
		return logAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).count();
	}

	@Nested
	@DisplayName("runExclusively()")
	class RunExclusively {

		@Test
		@DisplayName("락 획득에 성공하면 task 를 실행하고 true 를 반환한다")
		void runsTaskWhenLockAcquired() throws SQLException {
			// given
			stubGetLock(1);
			stubReleaseLock(1);
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = aggregationLock.runExclusively(task);

			// then
			assertThat(acquired).isTrue();
			verify(task).run();
			verify(connection).close();
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
			given(connectionProvider.open()).willThrow(new SQLException("connection refused"));
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = aggregationLock.runExclusively(task);

			// then
			assertThat(acquired).isFalse();
			verify(task, never()).run();
		}

		@Test
		@DisplayName("task 가 예외를 던져도 락 커넥션은 반드시 닫힌다")
		void closesLockConnectionEvenWhenTaskThrows() throws SQLException {
			// given
			stubGetLock(1);
			stubReleaseLock(1);
			Runnable task = () -> {
				throw new IllegalStateException("boom");
			};

			// when & then
			assertThatThrownBy(() -> aggregationLock.runExclusively(task))
					.isInstanceOf(IllegalStateException.class);
			verify(connection).close();
		}

		@Test
		@DisplayName("RELEASE_LOCK 이 비정상 반환값을 주면 error 로 남긴다")
		void logsErrorWhenReleaseReturnsUnexpectedValue() throws SQLException {
			// given
			stubGetLock(1);
			stubReleaseLock(0);
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = aggregationLock.runExclusively(task);

			// then
			assertThat(acquired).isTrue();
			assertThat(errorLogCount()).isPositive();
		}

		@Test
		@DisplayName("RELEASE_LOCK 이 NULL 을 주면 error 로 남긴다")
		void logsErrorWhenReleaseReturnsNull() throws SQLException {
			// given
			stubGetLock(1);
			stubReleaseLock(null);
			Runnable task = mock(Runnable.class);

			// when
			aggregationLock.runExclusively(task);

			// then
			assertThat(errorLogCount()).isPositive();
		}

		@Test
		@DisplayName("락 획득 실패가 연속되면 error 로 드러낸다")
		void logsErrorWhenAcquireFailsRepeatedly() throws SQLException {
			// given
			stubGetLock(0);
			Runnable task = mock(Runnable.class);

			// when
			for (int i = 0; i < 3; i++) {
				aggregationLock.runExclusively(task);
			}

			// then
			assertThat(errorLogCount()).isPositive();
			verify(task, never()).run();
		}

		@Test
		@DisplayName("락 획득에 성공하면 연속 실패 카운트가 초기화된다")
		void resetsConsecutiveFailureCountOnSuccess() throws SQLException {
			// given: 두 번 연속 실패
			stubGetLock(0);
			aggregationLock.runExclusively(mock(Runnable.class));
			aggregationLock.runExclusively(mock(Runnable.class));

			// when: 성공 후 다시 두 번 실패해도 (총 4번째 연속 실패까지는 못 감) 아직 error 는 없어야 한다
			stubGetLock(1);
			stubReleaseLock(1);
			aggregationLock.runExclusively(mock(Runnable.class));
			logAppender.list.clear();

			stubGetLock(0);
			aggregationLock.runExclusively(mock(Runnable.class));
			aggregationLock.runExclusively(mock(Runnable.class));

			// then
			assertThat(errorLogCount()).isZero();
		}
	}
}
