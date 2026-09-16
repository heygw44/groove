package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.limited.config.LimitedCircuitProperties;

class LimitedFallbackGateTest {

	private LimitedFallbackGate fallbackGate;

	@BeforeEach
	void setUp() {
		LimitedCircuitProperties properties = new LimitedCircuitProperties(5, Duration.ofSeconds(10), 2, true);
		fallbackGate = new LimitedFallbackGate(properties);
	}

	@Nested
	@DisplayName("tryEnter()")
	class TryEnter {

		@Test
		@DisplayName("permit 이 남아 있으면 true 를 반환한다")
		void returnsTrueWhilePermitsRemain() {
			// when & then
			assertThat(fallbackGate.tryEnter(1L)).isTrue();
			assertThat(fallbackGate.tryEnter(1L)).isTrue();
		}

		@Test
		@DisplayName("permit 이 소진되면 false 를 반환한다")
		void returnsFalseWhenPermitsExhausted() {
			// given
			fallbackGate.tryEnter(1L);
			fallbackGate.tryEnter(1L);

			// when & then
			assertThat(fallbackGate.tryEnter(1L)).isFalse();
		}

		@Test
		@DisplayName("드롭마다 독립적으로 permit 을 관리한다")
		void managesPermitsPerDropIndependently() {
			// given
			fallbackGate.tryEnter(1L);
			fallbackGate.tryEnter(1L);

			// when & then
			assertThat(fallbackGate.tryEnter(2L)).isTrue();
		}
	}

	@Nested
	@DisplayName("exit()")
	class Exit {

		@Test
		@DisplayName("exit 으로 반환하면 소진된 permit 을 다시 얻을 수 있다")
		void allowsReentryAfterExit() {
			// given
			fallbackGate.tryEnter(1L);
			fallbackGate.tryEnter(1L);
			assertThat(fallbackGate.tryEnter(1L)).isFalse();

			// when
			fallbackGate.exit(1L);

			// then
			assertThat(fallbackGate.tryEnter(1L)).isTrue();
		}
	}
}
