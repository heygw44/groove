package com.groove.global.alert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertSuppressionFlushSchedulerTest {

	@Mock
	private AlertNotifier alertNotifier;

	@Nested
	@DisplayName("flush()")
	class Flush {

		@Test
		@DisplayName("알림 구현체의 flushSuppressed() 를 호출한다")
		void callsFlushSuppressedOnNotifier() {
			// given
			willDoNothing().given(alertNotifier).flushSuppressed();
			AlertSuppressionFlushScheduler scheduler = new AlertSuppressionFlushScheduler(alertNotifier);

			// when
			scheduler.flush();

			// then
			verify(alertNotifier).flushSuppressed();
		}

		@Test
		@DisplayName("flushSuppressed() 가 예외를 던져도 전파하지 않는다")
		void doesNotPropagateExceptionFromNotifier() {
			// given
			willThrow(new RuntimeException("전송 실패")).given(alertNotifier).flushSuppressed();
			AlertSuppressionFlushScheduler scheduler = new AlertSuppressionFlushScheduler(alertNotifier);

			// when & then
			assertThatCode(scheduler::flush).doesNotThrowAnyException();
		}
	}
}
