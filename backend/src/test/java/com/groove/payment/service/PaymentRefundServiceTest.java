package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

	private static final Long PAYMENT_ID = 20L;
	private static final Long PAYMENT_CANCEL_ID = 90L;
	private static final String PAYMENT_KEY = "tviva-refund-key";
	private static final String IDEMPOTENCY_KEY = "cancel-" + PAYMENT_KEY + "-1";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	PaymentRefundWriter writer;

	@Mock
	PaymentClient paymentClient;

	PaymentRefundService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		service = new PaymentRefundService(writer, paymentClient, clock);
	}

	private PaymentRefundRequest request() {
		return new PaymentRefundRequest(PAYMENT_ID, PAYMENT_CANCEL_ID, PAYMENT_KEY, new BigDecimal("10000"), "사유",
				IDEMPOTENCY_KEY, null, null);
	}

	@Nested
	@DisplayName("refund()")
	class Refund {

		@Test
		@DisplayName("토스 부분취소가 성공하면 DONE 을 반환한다")
		void completesRefund() {
			// given
			given(writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null)).willReturn(request());
			given(paymentClient.cancel(PaymentCancelCommand.of(PAYMENT_KEY, "사유", new BigDecimal("10000"),
					IDEMPOTENCY_KEY, null)))
					.willReturn(new PaymentCancelResult(PAYMENT_KEY, "PARTIAL_CANCELED", NOW, "txn-1", null));

			// when
			PaymentRefundResult result = service.refund(PAYMENT_ID, new BigDecimal("10000"), "사유", null);

			// then
			assertThat(result.status()).isEqualTo(PaymentRefundStatus.DONE);
			assertThat(result.canceledAmount()).isEqualByComparingTo("10000");
			verify(writer).completeRefund(PAYMENT_ID, PAYMENT_CANCEL_ID, new BigDecimal("10000"), "txn-1", NOW);
		}

		@Test
		@DisplayName("토스 응답에 취소 시각이 없으면 현재 시각을 사용한다")
		void usesCurrentTimeWhenCanceledAtMissing() {
			// given
			given(writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null)).willReturn(request());
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willReturn(new PaymentCancelResult(PAYMENT_KEY, "PARTIAL_CANCELED", null, "txn-1", null));

			// when
			service.refund(PAYMENT_ID, new BigDecimal("10000"), "사유", null);

			// then
			verify(writer).completeRefund(PAYMENT_ID, PAYMENT_CANCEL_ID, new BigDecimal("10000"), "txn-1", NOW);
		}

		@Test
		@DisplayName("이미 진행 중인 취소 건이 있으면 토스를 호출하지 않고 예외를 그대로 전파한다")
		void propagatesInProgressExceptionWithoutCallingToss() {
			// given
			BusinessException inProgress = new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
			willThrow(inProgress).given(writer).requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null);

			// when & then
			assertThatThrownBy(() -> service.refund(PAYMENT_ID, new BigDecimal("10000"), "사유", null))
					.isSameAs(inProgress);
			verify(paymentClient, never()).cancel(any(PaymentCancelCommand.class));
		}

		@Test
		@DisplayName("토스 결과를 알 수 없으면 요청 상태를 되돌리지 않고 예외를 다시 던진다")
		void keepsRequestWhenTossResultIsUnknown() {
			// given
			given(writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null)).willReturn(request());
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			willThrow(resultUnknown).given(paymentClient).cancel(any(PaymentCancelCommand.class));

			// when & then
			assertThatThrownBy(() -> service.refund(PAYMENT_ID, new BigDecimal("10000"), "사유", null))
					.isSameAs(resultUnknown);
			verify(writer, never()).failRefund(any());
		}

		@Test
		@DisplayName("토스가 취소를 거절하면 요청 상태를 되돌리고 예외를 다시 던진다")
		void revertsRequestWhenTossRejects() {
			// given
			given(writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null)).willReturn(request());
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
			willThrow(failure).given(paymentClient).cancel(any(PaymentCancelCommand.class));

			// when & then
			assertThatThrownBy(() -> service.refund(PAYMENT_ID, new BigDecimal("10000"), "사유", null))
					.isSameAs(failure);
			verify(writer).failRefund(PAYMENT_CANCEL_ID);
		}

		@Test
		@DisplayName("토스 취소 후 반영이 실패하면 IN_PROGRESS 를 반환한다")
		void keepsInProgressWhenCompletionFails() {
			// given
			given(writer.requestRefund(PAYMENT_ID, new BigDecimal("10000"), "사유", null)).willReturn(request());
			given(paymentClient.cancel(any(PaymentCancelCommand.class)))
					.willReturn(new PaymentCancelResult(PAYMENT_KEY, "PARTIAL_CANCELED", NOW, "txn-1", null));
			willThrow(new IllegalStateException("T2 failed")).given(writer)
					.completeRefund(PAYMENT_ID, PAYMENT_CANCEL_ID, new BigDecimal("10000"), "txn-1", NOW);

			// when
			PaymentRefundResult result = service.refund(PAYMENT_ID, new BigDecimal("10000"), "사유", null);

			// then
			assertThat(result.status()).isEqualTo(PaymentRefundStatus.IN_PROGRESS);
		}
	}
}
