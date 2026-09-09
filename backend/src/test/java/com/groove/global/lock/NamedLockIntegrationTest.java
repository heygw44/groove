package com.groove.global.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.groove.support.IntegrationTestSupport;

/**
 * 실제 MySQL(Testcontainers) 을 대상으로 {@link NamedLock} 을 검증한다.
 * 특히 서로 다른 락 이름은 서로를 막지 않아야 한다는 점 - 이게 이 유틸을 추출한 이유다.
 */
class NamedLockIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private DataSource dataSource;

	private NamedLock namedLock() {
		return new NamedLock(dataSource, LoggerFactory.getLogger(NamedLockIntegrationTest.class));
	}

	private Integer currentLockHolder(String lockName) {
		return new JdbcTemplate(dataSource)
				.queryForObject("SELECT IS_USED_LOCK(?)", Integer.class, lockName);
	}

	@Nested
	@DisplayName("runExclusively()")
	class RunExclusively {

		@Test
		@DisplayName("반복 호출 뒤에도 락이 다른 커넥션 관점에서 매번 풀린다")
		void releasesLockAfterEveryCallAsSeenFromAnotherConnection() {
			NamedLock namedLock = namedLock();
			String lockName = "groove:named-lock-it-basic";

			for (int i = 0; i < 10; i++) {
				boolean acquired = namedLock.runExclusively(lockName, () -> {
				});

				assertThat(acquired).isTrue();
				assertThat(currentLockHolder(lockName))
						.as("반복 %d회차 이후에는 다른 커넥션에서 봤을 때 락이 풀려 있어야 한다", i)
						.isNull();
			}
		}

		@Test
		@DisplayName("서로 다른 두 락 이름은 서로를 막지 않는다")
		void differentLockNamesDoNotBlockEachOther()
				throws InterruptedException, ExecutionException, TimeoutException {
			NamedLock namedLock = namedLock();
			String lockNameA = "groove:named-lock-it-a";
			String lockNameB = "groove:named-lock-it-b";

			CountDownLatch holdingA = new CountDownLatch(1);
			CountDownLatch releaseA = new CountDownLatch(1);

			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<Boolean> acquiredA = pool.submit(() -> namedLock.runExclusively(lockNameA, () -> {
					holdingA.countDown();
					awaitQuietly(releaseA);
				}));

				assertThat(holdingA.await(10, TimeUnit.SECONDS)).isTrue();

				Future<Boolean> acquiredB = pool.submit(() -> namedLock.runExclusively(lockNameB, () -> {
				}));

				assertThat(acquiredB.get(5, TimeUnit.SECONDS))
						.as("lockNameA 가 잡혀 있는 동안에도 lockNameB 는 즉시 잡을 수 있어야 한다")
						.isTrue();
				assertThat(currentLockHolder(lockNameB)).isNull();

				releaseA.countDown();
				assertThat(acquiredA.get(10, TimeUnit.SECONDS)).isTrue();
				assertThat(currentLockHolder(lockNameA)).isNull();
			} finally {
				pool.shutdownNow();
			}
		}
	}

	private void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
