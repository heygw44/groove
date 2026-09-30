package com.groove.payment.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentCancelRetryCandidate;
import com.groove.payment.dto.PaymentCompensationCandidate;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentCompensationRepository;
import com.groove.payment.service.CompensationResult;
import com.groove.payment.service.PaymentCancelRetrier;
import com.groove.payment.service.PaymentCompensationRetrier;
import com.groove.payment.service.PaymentCompensator;
import com.groove.payment.service.PaymentReconcileLock;
import com.groove.payment.service.PaymentReconcileOutcome;
import com.groove.payment.service.PaymentReconcileService;

@ExtendWith(MockitoExtension.class)
class PaymentReconcileSchedulerTest {

	private static final String RETRY_KEY = "cancel-tviva-key-2";

	@Mock
	private PaymentReconcileService reconcileService;

	@Mock
	private PaymentReconcileLock reconcileLock;

	@Mock
	private PaymentClient paymentClient;

	@Mock
	private PaymentCompensator compensator;

	@Mock
	private PaymentCompensationRepository compensationRepository;

	@Mock
	private PaymentCompensationRetrier compensationRetrier;

	@Mock
	private PaymentCancelRepository paymentCancelRepository;

	@Mock
	private PaymentCancelRetrier paymentCancelRetrier;

	@Mock
	private ShutdownSignal shutdownSignal;

	@Mock
	private AlertNotifier alertNotifier;

	private PaymentReconcileScheduler scheduler;

	private Clock clock;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		PaymentReconcileProperties reconcileProperties = new PaymentReconcileProperties(Duration.ofSeconds(60),
				Duration.ofMinutes(2), 50, 10, Duration.ofMinutes(1));
		scheduler = new PaymentReconcileScheduler(reconcileService, reconcileLock, paymentClient, compensator,
				compensationRepository, compensationRetrier, paymentCancelRepository, paymentCancelRetrier,
				reconcileProperties, shutdownSignal, clock, alertNotifier);
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
		@DisplayName("대사 실패 기록마저 실패하면 경보를 보낸다")
		void notifiesAlertWhenRecordFailureAlsoThrows() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			given(reconcileService.findCandidates(now)).willReturn(List.of(candidate));
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			given(paymentClient.lookup("toss-1")).willThrow(resultUnknown);
			willThrow(new IllegalStateException("db down")).given(reconcileService)
					.recordFailure(candidate, resultUnknown.getMessage());

			// when
			scheduler.reconcile();

			// then
			verify(alertNotifier).notify(any(Alert.class));
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
		@DisplayName("취소 재시도가 필요한 결과면 토스 취소 후 결과를 기록한다")
		void retriesCancelWhenOutcomeNeedsCancelRetry() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			given(reconcileService.findCandidates(now)).willReturn(List.of(candidate));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, "tviva-key", "카드",
					BigDecimal.ZERO, now.minusMinutes(5), null);
			given(paymentClient.lookup("toss-1")).willReturn(lookup);
			given(reconcileService.apply(candidate, lookup))
					.willReturn(PaymentReconcileOutcome.needsCancelRetry("tviva-key", RETRY_KEY));
			PaymentCancelResult cancelResult = PaymentCancelResult.of("tviva-key", "CANCELED", now);
			given(paymentClient.cancel(retryCommand())).willReturn(cancelResult);

			// when
			scheduler.reconcile();

			// then
			verify(paymentClient).cancel(retryCommand());
			verify(reconcileService).recordCancelRetry(candidate, cancelResult, null);
		}

		@Test
		@DisplayName("취소 재시도가 거절되면 거절 결과를 기록한다")
		void recordsCancelRetryRejection() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			given(reconcileService.findCandidates(now)).willReturn(List.of(candidate));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, "tviva-key", "카드",
					BigDecimal.ZERO, now.minusMinutes(5), null);
			given(paymentClient.lookup("toss-1")).willReturn(lookup);
			given(reconcileService.apply(candidate, lookup))
					.willReturn(PaymentReconcileOutcome.needsCancelRetry("tviva-key", RETRY_KEY));
			BusinessException rejection = new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
			given(paymentClient.cancel(retryCommand())).willThrow(rejection);

			// when
			scheduler.reconcile();

			// then
			verify(reconcileService).recordCancelRetry(candidate, null, rejection);
		}

		@Test
		@DisplayName("취소 재시도 결과가 불명이면 결과 불명으로 기록한다")
		void recordsUnknownCancelRetryResult() {
			// given
			stubLockToRunTask();
			PaymentReconcileCandidate candidate = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			given(reconcileService.findCandidates(now)).willReturn(List.of(candidate));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.DONE, "tviva-key", "카드",
					BigDecimal.ZERO, now.minusMinutes(5), null);
			given(paymentClient.lookup("toss-1")).willReturn(lookup);
			given(reconcileService.apply(candidate, lookup))
					.willReturn(PaymentReconcileOutcome.needsCancelRetry("tviva-key", RETRY_KEY));
			RuntimeException timeout = new RuntimeException("Read timed out");
			given(paymentClient.cancel(retryCommand())).willThrow(timeout);

			// when
			scheduler.reconcile();

			// then
			verify(reconcileService).recordCancelRetry(eq(candidate), eq(null), any(BusinessException.class));
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

		@Test
		@DisplayName("시작 시 셧다운 중이면 락도 잡지 않는다")
		void doesNotAcquireLockWhenShuttingDownAtStart() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(true);

			// when
			scheduler.reconcile();

			// then
			verify(reconcileLock, never()).runExclusively(any());
		}

		@Test
		@DisplayName("첫 건 처리 후 셧다운 신호가 오면 두 번째 건을 처리하지 않는다")
		void stopsProcessingWhenShutdownSignaledMidLoop() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(false, false, true);
			stubLockToRunTask();
			PaymentReconcileCandidate first = new PaymentReconcileCandidate(1L, 10L, "toss-1");
			PaymentReconcileCandidate second = new PaymentReconcileCandidate(2L, 20L, "toss-2");
			given(reconcileService.findCandidates(now)).willReturn(List.of(first, second));
			PaymentLookupResult lookup = new PaymentLookupResult(PaymentLookupStatus.READY, null, null, null, null,
					null);
			given(paymentClient.lookup("toss-1")).willReturn(lookup);
			given(reconcileService.apply(first, lookup)).willReturn(PaymentReconcileOutcome.applied());

			// when
			scheduler.reconcile();

			// then
			verify(paymentClient).lookup("toss-1");
			verify(paymentClient, never()).lookup("toss-2");
		}
	}

	@Nested
	@DisplayName("reconcile() 의 보상 대기 회수")
	class ReconcileCompensations {

		@Test
		@DisplayName("후보마다 회수를 시도한다")
		void retriesEachCandidate() {
			// given
			stubLockToRunTask();
			given(reconcileService.findCandidates(now)).willReturn(List.of());
			PaymentCompensationCandidate candidate = new PaymentCompensationCandidate("tviva-dup", "중복 승인 자동 취소");
			given(compensationRepository.findCandidates(eq(now.minusMinutes(2)), eq(10), any()))
					.willReturn(List.of(candidate));

			// when
			scheduler.reconcile();

			// then
			verify(compensationRetrier).retry(candidate);
		}

		@Test
		@DisplayName("한 건 회수 처리가 예외를 던져도 대사 자체는 끝까지 진행된다")
		void doesNotPropagateWhenRetrierThrows() {
			// given
			stubLockToRunTask();
			given(reconcileService.findCandidates(now)).willReturn(List.of());
			PaymentCompensationCandidate first = new PaymentCompensationCandidate("tviva-dup", "중복 승인 자동 취소");
			PaymentCompensationCandidate second = new PaymentCompensationCandidate("tviva-dup-2", "중복 승인 자동 취소");
			given(compensationRepository.findCandidates(eq(now.minusMinutes(2)), eq(10), any()))
					.willReturn(List.of(first, second));
			willThrow(new IllegalStateException("boom")).given(compensationRetrier).retry(first);

			// when
			scheduler.reconcile();

			// then: 예외를 던지지 않고 나머지 후보도 처리한다.
			verify(compensationRetrier).retry(second);
		}
	}

	@Nested
	@DisplayName("reconcile() 의 부분취소 재시도 회수")
	class ReconcileRefundRetries {

		@Test
		@DisplayName("후보마다 재시도를 시도한다")
		void retriesEachCandidate() {
			// given
			stubLockToRunTask();
			given(reconcileService.findCandidates(now)).willReturn(List.of());
			PaymentCancelRetryCandidate candidate = new PaymentCancelRetryCandidate(1L, 10L, "tviva-refund", "toss-1",
					BigDecimal.TEN, "cancel-tviva-refund-1", "부분 반품", now.minusMinutes(2), null);
			given(paymentCancelRepository.findRetryCandidates(eq(now.minusMinutes(1)), any()))
					.willReturn(List.of(candidate));

			// when
			scheduler.reconcile();

			// then
			verify(paymentCancelRetrier).retry(candidate);
		}

		@Test
		@DisplayName("한 건 회수 처리가 예외를 던져도 대사 자체는 끝까지 진행된다")
		void doesNotPropagateWhenRetrierThrows() {
			// given
			stubLockToRunTask();
			given(reconcileService.findCandidates(now)).willReturn(List.of());
			PaymentCancelRetryCandidate first = new PaymentCancelRetryCandidate(1L, 10L, "tviva-refund-1", "toss-1",
					BigDecimal.TEN, "cancel-tviva-refund-1-1", "부분 반품", now.minusMinutes(2), null);
			PaymentCancelRetryCandidate second = new PaymentCancelRetryCandidate(2L, 20L, "tviva-refund-2", "toss-2",
					BigDecimal.TEN, "cancel-tviva-refund-2-1", "부분 반품", now.minusMinutes(2), null);
			given(paymentCancelRepository.findRetryCandidates(eq(now.minusMinutes(1)), any()))
					.willReturn(List.of(first, second));
			willThrow(new IllegalStateException("boom")).given(paymentCancelRetrier).retry(first);

			// when
			scheduler.reconcile();

			// then: 예외를 던지지 않고 나머지 후보도 처리한다.
			verify(paymentCancelRetrier).retry(second);
		}
	}

	private PaymentCancelCommand retryCommand() {
		return PaymentCancelCommand.of("tviva-key", "주문 취소 재시도", null, RETRY_KEY, null);
	}

	private void stubLockToRunTask() {
		given(reconcileLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
