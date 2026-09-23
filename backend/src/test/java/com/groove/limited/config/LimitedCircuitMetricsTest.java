package com.groove.limited.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.global.alert.AlertNotifier;
import com.groove.limited.service.LimitedRedisCircuitBreaker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LimitedCircuitMetricsTest {

	private MutableClock clock;
	private LimitedRedisCircuitBreaker circuitBreaker;
	private SimpleMeterRegistry registry;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-09-23T00:00:00Z"));
		AlertNotifier alertNotifier = mock(AlertNotifier.class);
		LimitedCircuitProperties properties = new LimitedCircuitProperties(3, Duration.ofSeconds(10), 5, true);
		circuitBreaker = new LimitedRedisCircuitBreaker(properties, clock, alertNotifier);
		registry = new SimpleMeterRegistry();
		new LimitedCircuitMetrics(circuitBreaker).bindTo(registry);
	}

	@Nested
	@DisplayName("bindTo()")
	class BindTo {

		@Test
		@DisplayName("CLOSED 면 0 을 보고한다")
		void reportsZeroWhenClosed() {
			// when & then
			assertThat(gaugeValue()).isEqualTo(0.0);
		}

		@Test
		@DisplayName("threshold 만큼 실패하면 OPEN 이 되어 1 을 보고한다")
		void reportsOneWhenOpen() {
			// given
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();

			// when & then
			assertThat(gaugeValue()).isEqualTo(1.0);
		}

		@Test
		@DisplayName("open duration 이 지나면 HALF_OPEN 이 되어 2 를 보고한다")
		void reportsTwoWhenHalfOpen() {
			// given
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();
			circuitBreaker.onFailure();
			clock.advance(Duration.ofSeconds(10));

			// when & then
			assertThat(gaugeValue()).isEqualTo(2.0);
		}
	}

	private double gaugeValue() {
		return registry.get("groove.limited.circuit.state").gauge().value();
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
