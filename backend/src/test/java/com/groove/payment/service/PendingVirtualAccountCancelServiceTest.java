package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.groove.payment.client.PaymentClient;

@ExtendWith(MockitoExtension.class)
class PendingVirtualAccountCancelServiceTest {

	private static final Long ORDER_ID = 10L;
	private static final Long MEMBER_ID = 1L;
	private static final Long PAYMENT_ID = 20L;
	private static final String PAYMENT_KEY = "tviva-va-cancel";

	@Mock
	PendingVirtualAccountCancelWriter writer;

	@Mock
	PaymentClient paymentClient;

	PendingVirtualAccountCancelService service;
	LocalDateTime now;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-28T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		service = new PendingVirtualAccountCancelService(writer, paymentClient, clock);
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("토스 계좌를 닫고 복원 결과의 한정반 드롭 id 를 반환한다")
		void closesAccountAndReturnsLimitedDropId() {
			// given
			given(writer.lock(ORDER_ID, MEMBER_ID))
					.willReturn(new PendingVirtualAccountCancelTarget(PAYMENT_ID, PAYMENT_KEY));
			given(writer.finalizeCancel(any(), any(), any(), any()))
					.willReturn(Optional.of(new LimitedRelease(30L, MEMBER_ID)));

			// when
			Long limitedDropId = service.cancel(ORDER_ID, MEMBER_ID, "고객 변심");

			// then
			assertThat(limitedDropId).isEqualTo(30L);
			verify(paymentClient).cancel(PAYMENT_KEY, "고객 변심");
			verify(writer).finalizeCancel(ORDER_ID, PAYMENT_ID, "고객 변심", now);
		}

		@Test
		@DisplayName("사유가 없으면 기본 사유를 쓴다")
		void usesDefaultReasonWhenMissing() {
			// given
			given(writer.lock(ORDER_ID, MEMBER_ID))
					.willReturn(new PendingVirtualAccountCancelTarget(PAYMENT_ID, PAYMENT_KEY));
			given(writer.finalizeCancel(any(), any(), any(), any())).willReturn(Optional.empty());

			// when
			Long limitedDropId = service.cancel(ORDER_ID, MEMBER_ID, null);

			// then
			assertThat(limitedDropId).isNull();
			verify(paymentClient).cancel(PAYMENT_KEY, "주문 취소");
			verify(writer).finalizeCancel(ORDER_ID, PAYMENT_ID, "주문 취소", now);
		}

		@Test
		@DisplayName("토스 계좌 폐쇄가 실패하면 finalize 를 부르지 않고 예외를 다시 던진다")
		void rethrowsWithoutFinalizingWhenCloseAccountFails() {
			// given
			given(writer.lock(ORDER_ID, MEMBER_ID))
					.willReturn(new PendingVirtualAccountCancelTarget(PAYMENT_ID, PAYMENT_KEY));
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
			willThrow(failure).given(paymentClient).cancel(PAYMENT_KEY, "고객 변심");

			// when & then
			assertThatThrownBy(() -> service.cancel(ORDER_ID, MEMBER_ID, "고객 변심")).isSameAs(failure);
			verify(writer, never()).finalizeCancel(any(), any(), any(), any());
		}
	}
}
