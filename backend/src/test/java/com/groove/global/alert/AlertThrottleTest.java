package com.groove.global.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.global.alert.AlertThrottle.ThrottleResult;

class AlertThrottleTest {

	private MutableClock clock;
	private AlertThrottle throttle;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
		throttle = new AlertThrottle(clock, Duration.ofMinutes(5));
	}

	private Alert alert(String targetId) {
		return Alert.warn("limited.release-failed", "선점 해제 실패", targetId);
	}

	@Nested
	@DisplayName("acquire()")
	class Acquire {

		@Test
		@DisplayName("같은 키를 window 안에서 다시 요청하면 억제한다")
		void suppressesSameKeyWithinWindow() {
			// given
			throttle.acquire(alert("paymentId=1"));

			// when
			ThrottleResult second = throttle.acquire(alert("paymentId=2"));

			// then
			assertThat(second.acquired()).isFalse();
			assertThat(second.carried()).isNull();
		}

		@Test
		@DisplayName("window 가 지나면 통과시키고 그사이 억제분을 carried 로 돌려준다")
		void returnsCarriedSummaryAfterWindowElapses() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			throttle.acquire(alert("paymentId=3"));
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=4"));

			// then
			assertThat(result.acquired()).isTrue();
			assertThat(result.carried()).isNotNull();
			assertThat(result.carried().count()).isEqualTo(2);
			assertThat(result.carried().samples()).containsExactly("paymentId=2", "paymentId=3");
		}

		@Test
		@DisplayName("억제된 게 없으면 carried 는 null 이다")
		void carriesNothingWhenNoSuppression() {
			// given
			throttle.acquire(alert("paymentId=1"));
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=2"));

			// then
			assertThat(result.acquired()).isTrue();
			assertThat(result.carried()).isNull();
		}

		@Test
		@DisplayName("window 를 지나 통과하면 상태를 초기화해 다음 억제분만 센다")
		void resetsStateAfterPassingThrough() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			clock.advance(Duration.ofMinutes(5));
			throttle.acquire(alert("paymentId=3"));
			throttle.acquire(alert("paymentId=4"));
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=5"));

			// then
			assertThat(result.carried().count()).isEqualTo(1);
			assertThat(result.carried().samples()).containsExactly("paymentId=4");
		}

		@Test
		@DisplayName("억제 표본은 5개까지만 모은다")
		void capsSamplesAtFive() {
			// given
			throttle.acquire(alert("paymentId=0"));
			for (int i = 1; i <= 7; i++) {
				throttle.acquire(alert("paymentId=" + i));
			}
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=8"));

			// then
			assertThat(result.carried().count()).isEqualTo(7);
			assertThat(result.carried().samples()).hasSize(5);
		}

		@Test
		@DisplayName("같은 대상은 표본에 중복으로 담지 않는다")
		void deduplicatesSampleTargets() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			throttle.acquire(alert("paymentId=2"));
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=3"));

			// then
			assertThat(result.carried().count()).isEqualTo(2);
			assertThat(result.carried().samples()).containsExactly("paymentId=2");
		}

		@Test
		@DisplayName("대상이 null 이면 건수에는 포함하되 표본에는 담지 않는다")
		void countsNullTargetWithoutSampling() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			throttle.acquire(alert(null));
			throttle.acquire(alert(null));
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=3"));

			// then
			assertThat(result.carried().count()).isEqualTo(3);
			assertThat(result.carried().samples()).containsExactly("paymentId=2");
		}

		@Test
		@DisplayName("억제되는 동안 더 높은 severity 가 오면 그걸로 유지한다")
		void keepsHighestSeverity() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(Alert.critical("limited.release-failed", "선점 해제 실패", "paymentId=2"));
			throttle.acquire(alert("paymentId=3"));
			clock.advance(Duration.ofMinutes(5));

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=4"));

			// then
			assertThat(result.carried().severity()).isEqualTo(AlertSeverity.CRITICAL);
		}

		@Test
		@DisplayName("다른 키는 서로 독립적으로 판단한다")
		void treatsDifferentKeysIndependently() {
			// given
			throttle.acquire(alert("paymentId=1"));

			// when
			ThrottleResult result = throttle.acquire(Alert.warn("payment.compensation-failed", "보상 취소 실패", null));

			// then
			assertThat(result.acquired()).isTrue();
		}

		@Test
		@DisplayName("스레드 여러 개가 동시에 요청해도 통과 수와 억제 건수 합이 전체 요청 수와 같다")
		void keepsCountConsistentUnderConcurrency() throws InterruptedException {
			// given
			int threadCount = 8;
			int perThread = 100;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch ready = new CountDownLatch(threadCount);
			CountDownLatch start = new CountDownLatch(1);
			CountDownLatch done = new CountDownLatch(threadCount);
			AtomicInteger acquiredCount = new AtomicInteger();

			for (int t = 0; t < threadCount; t++) {
				executor.execute(() -> {
					ready.countDown();
					awaitQuietly(start);
					for (int i = 0; i < perThread; i++) {
						ThrottleResult result = throttle.acquire(alert("paymentId=x"));
						if (result.acquired()) {
							acquiredCount.incrementAndGet();
						}
					}
					done.countDown();
				});
			}
			ready.await();

			// when
			start.countDown();
			done.await();
			executor.shutdown();
			clock.advance(Duration.ofMinutes(5));
			List<SuppressedSummary> summaries = throttle.drainExpired();

			// then
			int suppressedCount = summaries.isEmpty() ? 0 : summaries.get(0).count();
			assertThat(acquiredCount.get() + suppressedCount).isEqualTo(threadCount * perThread);
		}
	}

	@Nested
	@DisplayName("drainExpired()")
	class DrainExpired {

		@Test
		@DisplayName("window 가 지난 키만 꺼내고 상태를 초기화한다")
		void drainsOnlyKeysPastWindow() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			clock.advance(Duration.ofMinutes(5));

			// when
			List<SuppressedSummary> summaries = throttle.drainExpired();

			// then
			assertThat(summaries).hasSize(1);
			assertThat(summaries.get(0).key()).isEqualTo("limited.release-failed");
			assertThat(summaries.get(0).count()).isEqualTo(1);
			assertThat(summaries.get(0).samples()).containsExactly("paymentId=2");
		}

		@Test
		@DisplayName("억제된 게 없는 키는 꺼내지 않는다")
		void skipsKeysWithoutSuppression() {
			// given
			throttle.acquire(alert("paymentId=1"));
			clock.advance(Duration.ofMinutes(5));

			// when
			List<SuppressedSummary> summaries = throttle.drainExpired();

			// then
			assertThat(summaries).isEmpty();
		}

		@Test
		@DisplayName("한 번 꺼낸 뒤 다시 호출하면 비어 있다")
		void returnsEmptyOnSecondCall() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			clock.advance(Duration.ofMinutes(5));
			throttle.drainExpired();

			// when
			List<SuppressedSummary> summaries = throttle.drainExpired();

			// then
			assertThat(summaries).isEmpty();
		}

		@Test
		@DisplayName("꺼낸 직후 같은 키로 요청하면 다시 억제한다")
		void suppressesAgainRightAfterDrain() {
			// given
			throttle.acquire(alert("paymentId=1"));
			throttle.acquire(alert("paymentId=2"));
			clock.advance(Duration.ofMinutes(5));
			throttle.drainExpired();

			// when
			ThrottleResult result = throttle.acquire(alert("paymentId=3"));

			// then
			assertThat(result.acquired()).isFalse();
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
