package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
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
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.dto.PaymentReconcileCandidate;

@ExtendWith(MockitoExtension.class)
class PaymentLateResultApplierTest {

	private static final Long PAYMENT_ID = 900L;
	private static final Long ORDER_ID = 500L;
	private static final String TOSS_ORDER_ID = "20260922-ABCDEFGH";
	private static final String PAYMENT_KEY = "webhook-key";

	@Mock
	private PaymentReconcileService reconcileService;

	@Mock
	private PaymentCompensator compensator;

	@Mock
	private PaymentClient paymentClient;

	private PaymentLateResultApplier applier;
	private PaymentReconcileCandidate candidate;

	@BeforeEach
	void setUp() {
		applier = new PaymentLateResultApplier(reconcileService, compensator, paymentClient);
		candidate = new PaymentReconcileCandidate(PAYMENT_ID, ORDER_ID, TOSS_ORDER_ID);
	}

	@Nested
	@DisplayName("apply()")
	class Apply {

		@Test
		@DisplayName("보상도 취소 재시도도 필요 없으면 후속 호출 없이 결과를 그대로 반환한다")
		void returnsOutcomeWhenNoFollowUpNeeded() {
			// given
			PaymentLookupResult lookup = doneLookup();
			given(reconcileService.applyLate(candidate, lookup, "settlement"))
					.willReturn(PaymentReconcileOutcome.applied());

			// when
			PaymentReconcileOutcome outcome = applier.apply(candidate, lookup, "settlement");

			// then
			assertThat(outcome.needsCompensation()).isFalse();
			assertThat(outcome.needsCancelRetry()).isFalse();
			verifyNoInteractions(compensator, paymentClient);
		}

		@Test
		@DisplayName("보상이 필요한 결과면 토스 취소 후 같은 detail 태그로 보상 결과를 기록한다")
		void compensatesWhenOutcomeNeedsCompensation() {
			// given
			PaymentLookupResult lookup = doneLookup();
			PaymentReconcileOutcome outcome = PaymentReconcileOutcome.needsCompensation(PAYMENT_KEY,
					lookup.approvedAt());
			given(reconcileService.applyLate(candidate, lookup, "webhook")).willReturn(outcome);
			CompensationResult compensationResult = CompensationResult.canceled(lookup.approvedAt());
			given(compensator.cancelApproved(PAYMENT_ID, PAYMENT_KEY, lookup.approvedAt(),
					PaymentCompensator.ORDER_INVALIDATED_REASON)).willReturn(compensationResult);

			// when
			applier.apply(candidate, lookup, "webhook");

			// then
			verify(compensator).cancelApproved(PAYMENT_ID, PAYMENT_KEY, lookup.approvedAt(),
					PaymentCompensator.ORDER_INVALIDATED_REASON);
			verify(reconcileService).recordCompensation(candidate, compensationResult, "webhook");
		}

		@Test
		@DisplayName("취소 재시도가 성공하면 결과를 기록한다")
		void retriesCancelSuccessfully() {
			// given
			PaymentLookupResult lookup = doneLookup();
			given(reconcileService.applyLate(candidate, lookup, "webhook"))
					.willReturn(PaymentReconcileOutcome.needsCancelRetry(PAYMENT_KEY));
			PaymentCancelResult cancelResult = new PaymentCancelResult(PAYMENT_KEY, "CANCELED", lookup.approvedAt());
			given(paymentClient.cancel(PAYMENT_KEY, "주문 취소 재시도")).willReturn(cancelResult);

			// when
			applier.apply(candidate, lookup, "webhook");

			// then
			verify(reconcileService).recordCancelRetry(candidate, cancelResult, null);
		}

		@Test
		@DisplayName("취소 재시도가 명확히 거절되면 그 예외를 그대로 기록한다")
		void recordsRejectionWhenCancelRetryThrowsBusinessException() {
			// given
			PaymentLookupResult lookup = doneLookup();
			given(reconcileService.applyLate(candidate, lookup, "webhook"))
					.willReturn(PaymentReconcileOutcome.needsCancelRetry(PAYMENT_KEY));
			BusinessException rejection = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, "TOSS 거절");
			given(paymentClient.cancel(PAYMENT_KEY, "주문 취소 재시도")).willThrow(rejection);

			// when
			applier.apply(candidate, lookup, "webhook");

			// then
			verify(reconcileService).recordCancelRetry(candidate, null, rejection);
		}

		@Test
		@DisplayName("취소 재시도 결과가 불명이면 PAYMENT_RESULT_UNKNOWN 으로 감싸 기록한다")
		void recordsUnknownResultWhenCancelRetryThrowsUnexpectedException() {
			// given
			PaymentLookupResult lookup = doneLookup();
			given(reconcileService.applyLate(candidate, lookup, "webhook"))
					.willReturn(PaymentReconcileOutcome.needsCancelRetry(PAYMENT_KEY));
			given(paymentClient.cancel(PAYMENT_KEY, "주문 취소 재시도")).willThrow(new RuntimeException("Read timed out"));

			// when
			applier.apply(candidate, lookup, "webhook");

			// then
			ArgumentCaptor<BusinessException> captor = ArgumentCaptor.forClass(BusinessException.class);
			verify(reconcileService).recordCancelRetry(eq(candidate), isNull(), captor.capture());
			assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		}
	}

	private PaymentLookupResult doneLookup() {
		return new PaymentLookupResult(PaymentLookupStatus.DONE, PAYMENT_KEY, "카드", new BigDecimal("75600"),
				LocalDateTime.of(2026, 9, 22, 10, 0), null);
	}
}
