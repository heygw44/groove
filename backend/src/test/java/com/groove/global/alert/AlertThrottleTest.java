package com.groove.global.alert;

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

class AlertThrottleTest {

	private MutableClock clock;
	private AlertThrottle throttle;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-09-22T00:00:00Z"));
		throttle = new AlertThrottle(clock, Duration.ofMinutes(5));
	}

	@Nested
	@DisplayName("tryAcquire()")
	class TryAcquire {

		@Test
		@DisplayName("같은 키를 window 안에서 다시 요청하면 false 를 반환한다")
		void returnsFalseForSameKeyWithinWindow() {
			// given
			boolean first = throttle.tryAcquire("limited.release-failed");

			// when
			boolean second = throttle.tryAcquire("limited.release-failed");

			// then
			assertThat(first).isTrue();
			assertThat(second).isFalse();
		}

		@Test
		@DisplayName("window 가 지나면 같은 키도 다시 통과시킨다")
		void returnsTrueForSameKeyAfterWindowElapses() {
			// given
			throttle.tryAcquire("limited.release-failed");
			clock.advance(Duration.ofMinutes(5));

			// when
			boolean acquired = throttle.tryAcquire("limited.release-failed");

			// then
			assertThat(acquired).isTrue();
		}

		@Test
		@DisplayName("window 직전이면 여전히 억제한다")
		void returnsFalseJustBeforeWindowElapses() {
			// given
			throttle.tryAcquire("limited.release-failed");
			clock.advance(Duration.ofMinutes(5).minusMillis(1));

			// when
			boolean acquired = throttle.tryAcquire("limited.release-failed");

			// then
			assertThat(acquired).isFalse();
		}

		@Test
		@DisplayName("다른 키는 서로 독립적으로 판단한다")
		void treatsDifferentKeysIndependently() {
			// given
			throttle.tryAcquire("limited.release-failed");

			// when
			boolean acquired = throttle.tryAcquire("payment.compensation-failed");

			// then
			assertThat(acquired).isTrue();
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
