package com.groove.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiscogsRateLimiterTest {

	private MutableClock clock;
	private RecordingSleeper sleeper;
	private DiscogsRateLimiter rateLimiter;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-09-06T00:00:00Z"));
		sleeper = new RecordingSleeper();
		rateLimiter = new DiscogsRateLimiter(clock, sleeper);
	}

	@Nested
	@DisplayName("acquire()")
	class Acquire {

		@Test
		@DisplayName("60개를 연속으로 소비하면 대기 없이 통과한다")
		void passesWithoutWaitingForFirstSixtyCalls() {
			// when
			for (int i = 0; i < 60; i++) {
				rateLimiter.acquire();
			}

			// then
			assertThat(sleeper.sleptDurations()).isEmpty();
		}

		@Test
		@DisplayName("61번째 호출은 다음 토큰이 채워질 때까지 1초 대기한다")
		void waitsOneSecondOnSixtyFirstCall() {
			// given
			for (int i = 0; i < 60; i++) {
				rateLimiter.acquire();
			}

			// when
			rateLimiter.acquire();

			// then
			assertThat(sleeper.sleptDurations()).containsExactly(Duration.ofSeconds(1));
		}

		@Test
		@DisplayName("시간이 흐르면 토큰이 리필돼 다시 대기 없이 통과한다")
		void refillsTokensAsTimePasses() {
			// given
			for (int i = 0; i < 60; i++) {
				rateLimiter.acquire();
			}
			clock.advance(Duration.ofSeconds(2));

			// when
			rateLimiter.acquire();
			rateLimiter.acquire();

			// then
			assertThat(sleeper.sleptDurations()).isEmpty();
		}

		@Test
		@DisplayName("blockFor 이후에는 그 시간만큼 대기한다")
		void waitsForBlockedDuration() {
			// given
			rateLimiter.blockFor(5);

			// when
			rateLimiter.acquire();

			// then
			assertThat(sleeper.sleptDurations()).containsExactly(Duration.ofSeconds(5));
		}
	}

	@Nested
	@DisplayName("updateRemaining()")
	class UpdateRemaining {

		@Test
		@DisplayName("남은 호출 수를 0으로 갱신하면 다음 acquire 가 대기한다")
		void waitsAfterRemainingSetToZero() {
			// given
			rateLimiter.updateRemaining(0);

			// when
			rateLimiter.acquire();

			// then
			assertThat(sleeper.sleptDurations()).containsExactly(Duration.ofSeconds(1));
		}

		@Test
		@DisplayName("실제 토큰보다 큰 값으로 갱신해도 늘어나지 않는다")
		void doesNotIncreaseTokensAboveActualCount() {
			// given
			for (int i = 0; i < 60; i++) {
				rateLimiter.acquire();
			}

			// when
			rateLimiter.updateRemaining(59);
			rateLimiter.acquire();

			// then
			assertThat(sleeper.sleptDurations()).containsExactly(Duration.ofSeconds(1));
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
