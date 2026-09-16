package com.groove.global.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;

class ShutdownSignalTest {

	@Nested
	@DisplayName("isShuttingDown()")
	class IsShuttingDown {

		@Test
		@DisplayName("초기 상태는 false 다")
		void isFalseInitially() {
			// given
			ShutdownSignal shutdownSignal = new ShutdownSignal();

			// when & then
			assertThat(shutdownSignal.isShuttingDown()).isFalse();
		}

		@Test
		@DisplayName("ContextClosedEvent 를 받으면 true 로 바뀐다")
		void becomesTrueAfterContextClosedEvent() {
			// given
			ShutdownSignal shutdownSignal = new ShutdownSignal();

			// when
			shutdownSignal.onApplicationEvent(mock(ContextClosedEvent.class));

			// then
			assertThat(shutdownSignal.isShuttingDown()).isTrue();
		}
	}
}
