package com.groove.payment.service;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.global.alert.AlertNotifier;

/**
 * AggregationLockTest 와 같은 방식: NamedLock 자체를 모킹하지 않고 그 아래 JDBC 커넥션 레이어만
 * {@link PaymentSettlementLock.LockConnectionProvider} 로 갈아끼워 실제 GET_LOCK/RELEASE_LOCK 흐름을 검증한다.
 */
class PaymentSettlementLockTest {

	private Connection connection;
	private PaymentSettlementLock.LockConnectionProvider connectionProvider;
	private AlertNotifier alertNotifier;
	private PaymentSettlementLock settlementLock;

	@BeforeEach
	void setUp() throws SQLException {
		connection = mock(Connection.class);
		connectionProvider = mock(PaymentSettlementLock.LockConnectionProvider.class);
		given(connectionProvider.open()).willReturn(connection);
		alertNotifier = mock(AlertNotifier.class);
		settlementLock = new PaymentSettlementLock(connectionProvider, alertNotifier);
	}

	private void stubGetLock(int result) throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(connection.prepareStatement(eq("SELECT GET_LOCK(?, 0)"))).willReturn(statement);
		given(statement.executeQuery()).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getInt(1)).willReturn(result);
	}

	private void stubReleaseLock(int result) throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(connection.prepareStatement(eq("SELECT RELEASE_LOCK(?)"))).willReturn(statement);
		given(statement.executeQuery()).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getInt(1)).willReturn(result);
	}

	/** 락 획득 실패 시 보유자 조회(IS_USED_LOCK)도 한 번은 타므로 항상 스텁해 둔다. */
	private void stubIsUsedLock(long holderConnectionId) throws SQLException {
		PreparedStatement statement = mock(PreparedStatement.class);
		ResultSet resultSet = mock(ResultSet.class);
		given(connection.prepareStatement(eq("SELECT IS_USED_LOCK(?)"))).willReturn(statement);
		given(statement.executeQuery()).willReturn(resultSet);
		given(resultSet.next()).willReturn(true);
		given(resultSet.getLong(1)).willReturn(holderConnectionId);
	}

	@Nested
	@DisplayName("runExclusively()")
	class RunExclusively {

		@Test
		@DisplayName("락 획득에 성공하면 task 를 실행하고 true 를 반환하며 커넥션을 닫는다")
		void runsTaskWhenLockAcquired() throws SQLException {
			// given
			stubGetLock(1);
			stubReleaseLock(1);
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = settlementLock.runExclusively(task);

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
			stubIsUsedLock(42L);
			Runnable task = mock(Runnable.class);

			// when
			boolean acquired = settlementLock.runExclusively(task);

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
			assertThatThrownBy(() -> settlementLock.runExclusively(task))
					.isInstanceOf(IllegalStateException.class);
			verify(connection).close();
		}
	}
}
