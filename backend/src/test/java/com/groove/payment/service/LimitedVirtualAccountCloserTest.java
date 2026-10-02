package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.dto.PaymentReconcileCandidate;

@ExtendWith(MockitoExtension.class)
class LimitedVirtualAccountCloserTest {

	private static final Long PAYMENT_ID = 900L;
	private static final Long ORDER_ID = 500L;
	private static final String TOSS_ORDER_ID = "20260922-ABCDEFGH";
	private static final String PAYMENT_KEY = "limited-va-key";

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentReconcileService reconcileService;

	private LimitedVirtualAccountCloser closer;
	private PaymentReconcileCandidate candidate;

	@BeforeEach
	void setUp() {
		closer = new LimitedVirtualAccountCloser(paymentClient, reconcileService);
		candidate = new PaymentReconcileCandidate(PAYMENT_ID, ORDER_ID, TOSS_ORDER_ID);
	}

	@Nested
	@DisplayName("close()")
	class Close {

		@Test
		@DisplayName("토스 계좌 폐쇄에 성공하면 실패 없이 결과를 기록한다")
		void recordsSuccessWhenTossCancelSucceeds() {
			// given
			given(paymentClient.cancel(PAYMENT_KEY, LimitedVirtualAccountCloser.REASON))
					.willReturn(PaymentCancelResult.of(PAYMENT_KEY, "CANCELED", LocalDateTime.of(2026, 9, 22, 10, 0)));

			// when
			closer.close(candidate, PAYMENT_KEY, "webhook");

			// then
			verify(reconcileService).recordLimitedVirtualAccountClose(candidate, null, "webhook");
		}

		@Test
		@DisplayName("토스가 폐쇄를 거절하면 그 예외를 그대로 기록한다")
		void recordsBusinessExceptionWhenTossRejects() {
			// given
			BusinessException rejection = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "TOSS 거절");
			given(paymentClient.cancel(PAYMENT_KEY, LimitedVirtualAccountCloser.REASON)).willThrow(rejection);

			// when
			closer.close(candidate, PAYMENT_KEY, null);

			// then
			verify(reconcileService).recordLimitedVirtualAccountClose(candidate, rejection, null);
		}

		@Test
		@DisplayName("예기치 못한 예외면 PAYMENT_RESULT_UNKNOWN 으로 감싸 기록한다")
		void wrapsUnexpectedExceptionAsResultUnknown() {
			// given
			given(paymentClient.cancel(PAYMENT_KEY, LimitedVirtualAccountCloser.REASON))
					.willThrow(new IllegalStateException("Read timed out"));

			// when
			closer.close(candidate, PAYMENT_KEY, null);

			// then
			ArgumentCaptor<BusinessException> captor = ArgumentCaptor.forClass(BusinessException.class);
			verify(reconcileService).recordLimitedVirtualAccountClose(eq(candidate), captor.capture(), isNull());
			assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			assertThat(captor.getValue().getMessage()).isEqualTo("Read timed out");
		}
	}
}
