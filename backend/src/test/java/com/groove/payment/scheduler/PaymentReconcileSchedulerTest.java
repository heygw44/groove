package com.groove.payment.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
import com.groove.payment.service.CompensationResult;
import com.groove.payment.service.PaymentCompensator;
import com.groove.payment.service.PaymentReconcileLock;
import com.groove.payment.service.PaymentReconcileOutcome;
import com.groove.payment.service.PaymentReconcileService;

@ExtendWith(MockitoExtension.class)
class PaymentReconcileSchedulerTest {

	@Mock
	private PaymentReconcileService reconcileService;

	@Mock
	private PaymentReconcileLock reconcileLock;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentCompensator compensator;

	private PaymentReconcileScheduler scheduler;

	private Clock clock;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		scheduler = new PaymentReconcileScheduler(reconcileService, reconcileLock, paymentClient, compensator, clock);
	}

	@Nested
	@DisplayName("reconcile()")
	class Reconcile {

		@Test
		@DisplayName("락 획득에 실패하면 아무것도 하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(reconcileLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.reconcile();

			// then
			verify(reconcileService, never()).findCandidates(any());
		}

		@Test
		@DisplayName("토스 조회가 예외를 던지면 대사 실패로 기록한다")
		void recordsFailureWhenLookupThrows() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			given(reconcileService.findCandidates(now)).willReturn(List.of(candidate));
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			given(paymentClient.lookup("toss-1")).willThrow(resultUnknown);

			// when
			scheduler.reconcile();

			// then
			verify(reconcileService).recordFailure(candidate, resultUnknown.getMessage());
			verify(reconcileService, never()).apply(any(), any());
		}

		@Test
		@DisplayName("보상이 필요한 결과면 토스 취소 후 보상 결과를 기록한다")
		void compensatesWhenOutcomeNeedsCompensation() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			given(reconcileService.findCandidates(now)).willReturn(List.of(candidate));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, "tviva-key", "카드",
					BigDecimal.ZERO, now.minusMinutes(5), null);
			given(paymentClient.lookup("toss-1")).willReturn(lookup);
			LocalDateTime approvedAt = now.minusMinutes(5);
			PaymentReconcileOutcome outcome = PaymentReconcileOutcome.needsCompensation("tviva-key", approvedAt);
			given(reconcileService.apply(candidate, lookup)).willReturn(outcome);
			CompensationResult compensationResult = CompensationResult.canceled(now);
			given(compensator.cancelApproved(1L, "tviva-key", approvedAt, PaymentCompensator.ORDER_INVALIDATED_REASON))
					.willReturn(compensationResult);

			// when
			scheduler.reconcile();

			// then
			verify(compensator).cancelApproved(1L, "tviva-key", approvedAt,
					PaymentCompensator.ORDER_INVALIDATED_REASON);
			verify(reconcileService).recordCompensation(candidate, compensationResult);
		}

		@Test
		@DisplayName("한 건이 예외를 던져도 나머지 후보는 계속 처리한다")
		void continuesProcessingWhenOneCandidateFails() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate first = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			PaymentReconcileCandidate second = new PaymentReconcileCandidate(2L, 20L, "toss-2");
			given(reconcileService.findCandidates(now)).willReturn(List.of(first, second));
			given(paymentClient.lookup("toss-1")).willThrow(new RuntimeException("boom"));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.READY, null, null, null, null,
					null);
			given(paymentClient.lookup("toss-2")).willReturn(lookup);
			given(reconcileService.apply(second, lookup)).willReturn(PaymentReconcileOutcome.applied());

			// when
			scheduler.reconcile();

			// then
			verify(reconcileService).recordFailure(eq(first), any());
			verify(reconcileService).apply(second, lookup);
		}
	}

	private void stubLockToRunTask() {
		given(reconcileLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
