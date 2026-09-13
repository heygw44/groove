package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.OrderStatus;
import com.groove.order.service.PaidOrderCancelResult;
import com.groove.order.service.PaidOrderCancelStatus;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;

@ExtendWith(MockitoExtension.class)
class PaidOrderCancelServiceTest {

	private static final Long ORDER_ID = 10L;
	private static final Long MEMBER_ID = 1L;
	private static final Long PAYMENT_ID = 20L;
	private static final String PAYMENT_KEY = "tviva-cancel-key";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);

	@Mock
	PaymentCancelWriter writer;

	@Mock
	PaymentClient paymentClient;

	PaidOrderCancelService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		service = new PaidOrderCancelService(writer, paymentClient, clock);
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("이미 요청된 취소면 토스를 다시 호출하지 않는다")
		void returnsInProgressWithoutCallingTossForDuplicate() {
			// given
			given(writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심")).willReturn(request(true));

			// when
			PaidOrderCancelResult result = service.cancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			assertThat(result.status()).isEqualTo(PaidOrderCancelStatus.IN_PROGRESS);
			assertThat(result.alreadyRequested()).isTrue();
			verify(paymentClient, never()).cancel(any(), any());
		}

		@Test
		@DisplayName("토스 취소와 DB 복구가 성공하면 CANCELED 를 반환한다")
		void completesCancel() {
			// given
			given(writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심")).willReturn(request(false));
			given(paymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.willReturn(new PaymentCancelResult(PAYMENT_KEY, "CANCELED", NOW));
			given(writer.completeCancel(ORDER_ID, PAYMENT_ID, NOW))
					.willReturn(Optional.of(new LimitedRelease(30L, MEMBER_ID)));

			// when
			PaidOrderCancelResult result = service.cancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			assertThat(result.status()).isEqualTo(PaidOrderCancelStatus.CANCELED);
			assertThat(result.limitedDropId()).isEqualTo(30L);
		}

		@Test
		@DisplayName("토스 결과를 알 수 없으면 CANCEL_REQUESTED 를 유지한다")
		void keepsRequestWhenTossResultIsUnknown() {
			// given
			given(writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심")).willReturn(request(false));
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN))
					.given(paymentClient).cancel(PAYMENT_KEY, "고객 변심");

			// when
			PaidOrderCancelResult result = service.cancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			assertThat(result.status()).isEqualTo(PaidOrderCancelStatus.IN_PROGRESS);
			verify(writer, never()).revertCancelRequest(any(), any());
		}

		@Test
		@DisplayName("토스가 취소를 거절하면 요청 상태를 되돌리고 예외를 다시 던진다")
		void revertsRequestWhenTossRejects() {
			// given
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
			given(writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심")).willReturn(request(false));
			willThrow(failure).given(paymentClient).cancel(PAYMENT_KEY, "고객 변심");

			// when & then
			assertThatThrownBy(() -> service.cancel(ORDER_ID, MEMBER_ID, "고객 변심")).isSameAs(failure);
			verify(writer).revertCancelRequest(ORDER_ID, PAYMENT_ID);
		}

		@Test
		@DisplayName("토스 취소 후 DB 복구가 실패하면 CANCEL_REQUESTED 를 유지한다")
		void keepsRequestWhenCompletionFails() {
			// given
			given(writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심")).willReturn(request(false));
			given(paymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.willReturn(new PaymentCancelResult(PAYMENT_KEY, "CANCELED", NOW));
			willThrow(new IllegalStateException("T2 failed"))
					.given(writer).completeCancel(ORDER_ID, PAYMENT_ID, NOW);

			// when
			PaidOrderCancelResult result = service.cancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			assertThat(result.status()).isEqualTo(PaidOrderCancelStatus.IN_PROGRESS);
		}

		@Test
		@DisplayName("토스 응답에 취소 시각이 없으면 현재 시각을 사용한다")
		void usesCurrentTimeWhenCanceledAtMissing() {
			// given
			given(writer.requestCancel(ORDER_ID, MEMBER_ID, "고객 변심")).willReturn(request(false));
			given(paymentClient.cancel(PAYMENT_KEY, "고객 변심"))
					.willReturn(new PaymentCancelResult(PAYMENT_KEY, "CANCELED", null));
			given(writer.completeCancel(ORDER_ID, PAYMENT_ID, NOW)).willReturn(Optional.empty());

			// when
			service.cancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			verify(writer).completeCancel(ORDER_ID, PAYMENT_ID, NOW);
		}
	}

	private CancelRequest request(boolean alreadyRequested) {
		return new CancelRequest(ORDER_ID, PAYMENT_ID, PAYMENT_KEY, "고객 변심", OrderStatus.PAID,
				alreadyRequested);
	}
}
