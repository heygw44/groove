package com.groove.global.lock;

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

import javax.sql.DataSource;

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

class NamedLockTest {

	private static final String LOCK_NAME = "groove:test-lock";

	private Connection connection;
	private NamedLock.LockConnectionProvider connectionProvider;
	private NamedLock namedLock;
	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUp() throws SQLException {
		connection = mock(Connection.class);
		connectionProvider = mock(NamedLock.LockConnectionProvider.class);
		given(connectionProvider.open()).willReturn(connection);

		logAppender = new ListAppender<>();
		logAppender.start();
		((Logger) LoggerFactory.getLogger(NamedLockTest.class)).addAppender(logAppender);
		namedLock = new NamedLock(connectionProvider, LoggerFactory.getLogger(NamedLockTest.class));
	}

	@AfterEach
	void tearDown() {
		((Logger) LoggerFactory.getLogger(NamedLockTest.class)).detachAppender(logAppender);
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
			boolean acquired = namedLock.runExclusively(LOCK_NAME, task);

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
			boolean acquired = namedLock.runExclusively(LOCK_NAME, task);

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
			boolean acquired = namedLock.runExclusively(LOCK_NAME, task);

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
			assertThatThrownBy(() -> namedLock.runExclusively(LOCK_NAME, task))
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
			boolean acquired = namedLock.runExclusively(LOCK_NAME, task);

			// then
			assertThat(acquired).isTrue();
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
				namedLock.runExclusively(LOCK_NAME, task);
			}

			// then
			assertThat(errorLogCount()).isPositive();
			verify(task, never()).run();
		}

		@Test
		@DisplayName("락 이름이 다르면 연속 실패 카운터가 서로 섞이지 않는다")
		void tracksConsecutiveFailuresPerLockNameSeparately() throws SQLException {
			// given: lockA 를 두 번 연속 실패시킨다 (아직 임계치 미달)
			stubGetLock(0);
			namedLock.runExclusively("lock-a", mock(Runnable.class));
			namedLock.runExclusively("lock-a", mock(Runnable.class));
			logAppender.list.clear();

			// when: lockB 를 처음으로 실패시킨다
			namedLock.runExclusively("lock-b", mock(Runnable.class));

			// then: lockB 는 아직 1회차라 error 가 없어야 한다
			assertThat(errorLogCount()).isZero();
		}
	}

	@Nested
	@DisplayName("생성자")
	class Constructor {

		@Test
		@DisplayName("HikariDataSource 가 아니면 예외를 던진다")
		void throwsWhenDataSourceIsNotHikari() {
			// given
			DataSource dataSource = mock(DataSource.class);

			// when & then
			assertThatThrownBy(() -> new NamedLock(dataSource, LoggerFactory.getLogger(NamedLockTest.class)))
					.isInstanceOf(IllegalStateException.class);
		}
	}
}
