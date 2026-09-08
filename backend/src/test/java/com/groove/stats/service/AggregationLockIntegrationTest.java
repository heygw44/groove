package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.groove.support.IntegrationTestSupport;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 실제 MySQL(Testcontainers) 을 대상으로 {@link AggregationLock} 을 여러 번/동시에 호출한 뒤,
 * 락을 잡았던 커넥션과 무관한 별도 커넥션에서 {@code IS_USED_LOCK} 을 조회해 실제로 풀렸는지 확인한다.
 * 풀에서 빌린 커넥션으로 검증하면 close() 가 세션을 끝내지 않아 숨어 있던 리크를 놓칠 수 있으므로,
 * 검증은 항상 {@link AggregationLock} 바깥의 새 JDBC 커넥션으로 한다.
 */
class AggregationLockIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private AggregationLock aggregationLock;

	@Autowired
	private DataSource dataSource;

	private Integer currentLockHolder() {
		return new JdbcTemplate(dataSource)
				.queryForObject("SELECT IS_USED_LOCK('groove:sales-agg')", Integer.class);
	}

	@Nested
	@DisplayName("runExclusively()")
	class RunExclusively {

		@Test
		@DisplayName("반복 호출 뒤에도 락이 다른 커넥션 관점에서 매번 풀린다")
		void releasesLockAfterEveryCallAsSeenFromAnotherConnection() {
			for (int i = 0; i < 10; i++) {
				boolean acquired = aggregationLock.runExclusively(() -> {
				});

				assertThat(acquired).isTrue();
				assertThat(currentLockHolder())
						.as("반복 %d회차 이후에는 다른 커넥션에서 봤을 때 락이 풀려 있어야 한다", i)
						.isNull();
			}
		}

		@Test
		@DisplayName("task 가 예외를 던져도 락은 다른 커넥션 관점에서 풀린다")
		void releasesLockEvenWhenTaskThrows() {
			assertThatThrownBy(() -> aggregationLock.runExclusively(() -> {
				throw new IllegalStateException("boom");
			})).isInstanceOf(IllegalStateException.class);

			assertThat(currentLockHolder()).isNull();
		}

		@Test
		@DisplayName("동시에 여러 스레드가 호출해도 끝나면 락이 남지 않는다")
		void leavesNoLockAfterConcurrentContention() throws InterruptedException {
			int threads = 16;
			int iterationsPerThread = 20;
			ExecutorService pool = Executors.newFixedThreadPool(threads);
			CountDownLatch latch = new CountDownLatch(threads);
			AtomicInteger acquiredCount = new AtomicInteger();
			List<Exception> errors = new ArrayList<>();

			for (int t = 0; t < threads; t++) {
				pool.submit(() -> {
					try {
						for (int i = 0; i < iterationsPerThread; i++) {
							if (aggregationLock.runExclusively(() -> {
							})) {
								acquiredCount.incrementAndGet();
							}
						}
					} catch (Exception e) {
						synchronized (errors) {
							errors.add(e);
						}
					} finally {
						latch.countDown();
					}
				});
			}

			boolean completed = latch.await(60, TimeUnit.SECONDS);
			pool.shutdownNow();

			assertThat(completed).isTrue();
			assertThat(errors).isEmpty();
			assertThat(acquiredCount.get()).isPositive();
			assertThat(currentLockHolder())
					.as("모든 스레드가 끝난 뒤에는 락이 하나도 남아 있으면 안 된다")
					.isNull();
		}
	}

	/**
	 * 원인 규명용 대조 테스트. named lock 재진입 카운터가 남았을 때(GET_LOCK 두 번, RELEASE_LOCK 한 번)
	 * 커넥션을 풀에 반납하면(close() 가 세션을 안 끝낸다) 락이 계속 남지만, 풀 밖에서 연 전용 커넥션은
	 * close() 가 곧 세션 종료라 같은 카운터 불일치에서도 락이 남지 않는다 - {@link AggregationLock} 이
	 * 전용 커넥션을 쓰는 이유다.
	 */
	@Nested
	@DisplayName("풀 커넥션 재사용 대 전용 커넥션")
	class PooledVersusDedicatedConnection {

		@Test
		@DisplayName("재진입 카운터가 남은 채 풀에 반납하면 다른 커넥션에서도 여전히 락이 잡혀 있다")
		void pooledConnectionKeepsLockHeldAfterUnbalancedRelease() throws Exception {
			String name = "groove:repro-pooled";

			try (Connection sameSession = dataSource.getConnection()) {
				getLock(sameSession, name);
				getLock(sameSession, name); // 재진입: 카운터 2
				releaseLock(sameSession, name); // 카운터 1 로만 줄어든다
			} // close() 는 풀 반납일 뿐, 세션은 살아 있다

			assertThat(new JdbcTemplate(dataSource)
					.queryForObject("SELECT IS_USED_LOCK(?)", Integer.class, name))
					.as("풀 커넥션은 close() 해도 세션이 안 끝나 카운터가 남은 락이 그대로 잡혀 있다")
					.isNotNull();
		}

		@Test
		@DisplayName("같은 카운터 불일치라도 전용 커넥션은 close() 로 세션이 끝나 락이 풀린다")
		void dedicatedConnectionReleasesLockOnCloseEvenAfterUnbalancedRelease() throws Exception {
			String name = "groove:repro-dedicated";
			HikariDataSource hikari = (HikariDataSource) dataSource;

			try (Connection dedicated = DriverManager.getConnection(
					hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword())) {
				getLock(dedicated, name);
				getLock(dedicated, name); // 재진입: 카운터 2
				releaseLock(dedicated, name); // 카운터 1 로만 줄어든다
			} // close() 가 실제 세션 종료라 남은 카운터와 무관하게 락이 풀린다

			assertThat(new JdbcTemplate(dataSource)
					.queryForObject("SELECT IS_USED_LOCK(?)", Integer.class, name))
					.as("전용 커넥션은 close() 가 세션 종료라 카운터가 남아 있어도 락이 풀린다")
					.isNull();
		}

		private void getLock(Connection connection, String name) throws Exception {
			try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
				statement.setString(1, name);
				try (ResultSet resultSet = statement.executeQuery()) {
					resultSet.next();
				}
			}
		}

		private void releaseLock(Connection connection, String name) throws Exception {
			try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
				statement.setString(1, name);
				try (ResultSet resultSet = statement.executeQuery()) {
					resultSet.next();
				}
			}
		}
	}
}
