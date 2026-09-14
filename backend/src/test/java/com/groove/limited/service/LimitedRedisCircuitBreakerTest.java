package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.limited.config.LimitedCircuitProperties;

class LimitedRedisCircuitBreakerTest {

	private MutableClock clock;
	private LimitedRedisCircuitBreaker circuitBreaker;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-09-14T00:00:00Z"));
		LimitedCircuitProperties properties = new LimitedCircuitProperties(3, Duration.ofSeconds(10), 5, true);
		circuitBreaker = new LimitedRedisCircuitBreaker(properties, clock);
	}

	@Nested
	@DisplayName("onFailure()")
	class Failure {

		@Test
		@DisplayName("threshold 미만이면 CLOSED 를 유지한다")
		void staysClosedBelowThreshold() {
			// when
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();

			// then
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.CLOSED);
			assertThat(circuitBreaker.allowRedis()).isTrue();
		}

		@Test
		@DisplayName("threshold 에 도달하면 OPEN 으로 전이한다")
		void opensOnReachingThreshold() {
			// when
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();

			// then
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.OPEN);
			assertThat(circuitBreaker.allowRedis()).isFalse();
		}
	}

	@Nested
	@DisplayName("allowRedis()")
	class AllowRedis {

		@Test
		@DisplayName("OPEN 상태에서는 openDuration 이 지나기 전까지 항상 false 다")
		void returnsFalseBeforeOpenDurationElapses() {
			// given
			openCircuit();
			clock.advance(Duration.ofSeconds(9));

			// when & then
			assertThat(circuitBreaker.allowRedis()).isFalse();
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.OPEN);
		}

		@Test
		@DisplayName("openDuration 이 지나면 HALF_OPEN 으로 보고하고 첫 호출만 true 를 반환한다")
		void allowsOnlyFirstCallAfterOpenDurationElapses() {
			// given
			openCircuit();
			clock.advance(Duration.ofSeconds(10));

			// when & then
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.HALF_OPEN);
			assertThat(circuitBreaker.allowRedis()).isTrue();
			assertThat(circuitBreaker.allowRedis()).isFalse();
			assertThat(circuitBreaker.allowRedis()).isFalse();
		}
	}

	@Nested
	@DisplayName("onSuccess()")
	class Success {

		@Test
		@DisplayName("HALF_OPEN 프로브가 성공하면 CLOSED 로 복귀하고 다시 Redis 를 허용한다")
		void closesAfterSuccessfulProbe() {
			// given
			openCircuit();
			clock.advance(Duration.ofSeconds(10));
			assertThat(circuitBreaker.allowRedis()).isTrue();

			// when
			circuitBreaker.onSuccess();

			// then
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.CLOSED);
			assertThat(circuitBreaker.allowRedis()).isTrue();
		}
	}

	@Nested
	@DisplayName("HALF_OPEN 프로브 실패")
	class ProbeFailure {

		@Test
		@DisplayName("HALF_OPEN 프로브가 실패하면 다시 OPEN 으로 돌아간다")
		void reopensAfterFailedProbe() {
			// given
			openCircuit();
			clock.advance(Duration.ofSeconds(10));
			assertThat(circuitBreaker.allowRedis()).isTrue();

			// when
			circuitBreaker.onFailure();

			// then
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.OPEN);
			assertThat(circuitBreaker.allowRedis()).isFalse();

			// and: openedAt 이 갱신돼 openDuration 이 다시 처음부터 흘러야 한다
			clock.advance(Duration.ofSeconds(9));
			assertThat(circuitBreaker.allowRedis()).isFalse();
			clock.advance(Duration.ofSeconds(1));
			assertThat(circuitBreaker.state()).isEqualTo(LimitedRedisCircuitBreaker.State.HALF_OPEN);
		}
	}

	@Nested
	@DisplayName("fallbackDrops()")
	class FallbackDrops {

		@Test
		@DisplayName("noteFallback 으로 기록한 드롭을 스냅샷으로 돌려주고 clearFallbackDrops 로 지운 것만 빠진다")
		void recordsAndClearsFallbackDrops() {
			// given
			circuitBreaker.noteFallback(1L);
			circuitBreaker.noteFallback(2L);

			// when
			circuitBreaker.clearFallbackDrops(Set.of(1L));

			// then
			assertThat(circuitBreaker.fallbackDrops()).containsExactly(2L);
		}
	}

	private void openCircuit() {
		circuitBreaker.onFailure();
		circuitBreaker.onFailure();
		circuitBreaker.onFailure();
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
