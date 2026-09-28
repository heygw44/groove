package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
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
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.service.PaymentLateResultApplier;

/**
 * 오케스트레이션(락·전이는 {@link OrderExpirationWriter}, 토스 호출만 여기서)만 검증한다. 락·상태 전이
 * 시나리오는 {@link OrderExpirationWriterTest} 가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final Long PAYMENT_ID = 30L;
	private static final String TOSS_ORDER_ID = "20260904-TESTAB12";
	private static final String PAYMENT_KEY = "tviva-va-key";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);

	@Mock
	private OrderExpirationWriter writer;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentLateResultApplier paymentLateResultApplier;

	private OrderExpirationService service;

	@BeforeEach
	void setUp() {
		service = new OrderExpirationService(writer, paymentClient, paymentLateResultApplier);
	}

	@Nested
	@DisplayName("expire()")
	class Expire {

		@Test
		@DisplayName("만료 대상이 아니면 false 를 반환하고 토스를 부르지 않는다")
		void returnsFalseWhenNotExpirable() {
			// given
			given(writer.checkExpirable(ORDER_ID, NOW)).willReturn(Optional.empty());

			// when
			boolean result = service.expire(ORDER_ID, NOW);

			// then
			assertThat(result).isFalse();
			verify(paymentClient, never()).lookup(any());
		}

		@Test
		@DisplayName("단순 만료 대상이면(가상계좌 아님) writer 가 이미 끝낸 것이라 true 만 반환한다")
		void returnsTrueForSimpleTargetWithoutCallingToss() {
			// given
			given(writer.checkExpirable(ORDER_ID, NOW)).willReturn(Optional.of(OrderExpirationTarget.simple()));

			// when
			boolean result = service.expire(ORDER_ID, NOW);

			// then
			assertThat(result).isTrue();
			verify(paymentClient, never()).lookup(any());
		}

		@Test
		@DisplayName("가상계좌 대상인데 토스가 이미 DONE 이면 대사를 적용하고 계좌는 닫지 않는다")
		void appliesLateResultWhenTossAlreadyDone() {
			// given
			given(writer.checkExpirable(ORDER_ID, NOW))
					.willReturn(Optional.of(OrderExpirationTarget.virtualAccount(ORDER_ID, PAYMENT_ID, TOSS_ORDER_ID,
							PAYMENT_KEY)));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, PAYMENT_KEY, "가상계좌", null,
					NOW, null);
			given(paymentClient.lookup(TOSS_ORDER_ID)).willReturn(lookup);

			// when
			boolean result = service.expire(ORDER_ID, NOW);

			// then
			assertThat(result).isTrue();
			verify(paymentLateResultApplier).apply(
					new PaymentReconcileCandidate(PAYMENT_ID, ORDER_ID, TOSS_ORDER_ID), lookup,
					"입금기한 만료 처리 중 입금 확인");
			verify(paymentClient, never()).cancel(any(), any());
			verify(writer, never()).finalizeVirtualAccountExpiry(any(), any(), any(), any());
		}

		@Test
		@DisplayName("가상계좌 대상이고 토스도 여전히 입금대기면 계좌를 닫고 만료를 확정한다")
		void closesAccountAndFinalizesWhenStillWaiting() {
			// given
			given(writer.checkExpirable(ORDER_ID, NOW))
					.willReturn(Optional.of(OrderExpirationTarget.virtualAccount(ORDER_ID, PAYMENT_ID, TOSS_ORDER_ID,
							PAYMENT_KEY)));
			given(paymentClient.lookup(TOSS_ORDER_ID))
					.willReturn(new PaymentLookupResult(PaymentLookupStatus.WAITING_FOR_DEPOSIT, PAYMENT_KEY, "가상계좌",
							null, null, null));
			given(writer.finalizeVirtualAccountExpiry(ORDER_ID, PAYMENT_ID, "입금기한 만료", NOW)).willReturn(true);

			// when
			boolean result = service.expire(ORDER_ID, NOW);

			// then
			assertThat(result).isTrue();
			verify(paymentClient).cancel(PAYMENT_KEY, "입금기한 만료");
			verify(writer).finalizeVirtualAccountExpiry(ORDER_ID, PAYMENT_ID, "입금기한 만료", NOW);
		}

		@Test
		@DisplayName("토스 재조회가 실패하면 다음 스케줄로 미루고 false 를 반환한다")
		void returnsFalseWhenLookupFails() {
			// given
			given(writer.checkExpirable(ORDER_ID, NOW))
					.willReturn(Optional.of(OrderExpirationTarget.virtualAccount(ORDER_ID, PAYMENT_ID, TOSS_ORDER_ID,
							PAYMENT_KEY)));
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN)).given(paymentClient)
					.lookup(TOSS_ORDER_ID);

			// when
			boolean result = service.expire(ORDER_ID, NOW);

			// then
			assertThat(result).isFalse();
			verify(writer, never()).finalizeVirtualAccountExpiry(any(), any(), any(), any());
		}

		@Test
		@DisplayName("계좌 폐쇄가 실패하면 다음 스케줄로 미루고 false 를 반환한다")
		void returnsFalseWhenCloseAccountFails() {
			// given
			given(writer.checkExpirable(ORDER_ID, NOW))
					.willReturn(Optional.of(OrderExpirationTarget.virtualAccount(ORDER_ID, PAYMENT_ID, TOSS_ORDER_ID,
							PAYMENT_KEY)));
			given(paymentClient.lookup(TOSS_ORDER_ID))
					.willReturn(new PaymentLookupResult(PaymentLookupStatus.WAITING_FOR_DEPOSIT, PAYMENT_KEY, "가상계좌",
							null, null, null));
			willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED)).given(paymentClient)
					.cancel(PAYMENT_KEY, "입금기한 만료");

			// when
			boolean result = service.expire(ORDER_ID, NOW);

			// then
			assertThat(result).isFalse();
			verify(writer, never()).finalizeVirtualAccountExpiry(any(), any(), any(), any());
		}
	}
}
