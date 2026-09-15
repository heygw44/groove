package com.groove.payment.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.service.CompensationResult;
import com.groove.payment.service.PaymentCompensator;
import com.groove.payment.service.PaymentReconcileLock;
import com.groove.payment.service.PaymentReconcileOutcome;
import com.groove.payment.service.PaymentReconcileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 토스와 DB 결제 상태가 어긋난 건을 주기적으로 수렴시킨다. {@code DiscogsResyncScheduler} 와 같은 구조로, 토스
 * 조회(HTTP)는 트랜잭션 밖에서 하고 적용은 {@link PaymentReconcileService} 의 짧은 트랜잭션 하나로 끝낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileScheduler {

	private final PaymentReconcileService reconcileService;
	private final PaymentReconcileLock reconcileLock;
	private final PaymentClient paymentClient;
	private final PaymentCompensator compensator;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	@Scheduled(fixedDelayString = "${groove.payment.reconcile.interval}", initialDelay = 45_000)
	public void reconcile() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = reconcileLock.runExclusively(this::runReconcile);
		if (!acquired) {
			log.info("결제 대사 락 획득 실패로 건너뛴다");
		}
	}

	private void runReconcile() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<PaymentReconcileCandidate> candidates = reconcileService.findCandidates(now);
		int processed = 0;
		int compensated = 0;
		int failed = 0;
		for (PaymentReconcileCandidate candidate : candidates) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 결제 대사 중단 processed={} remaining={}", processed, candidates.size() - processed);
				break;
			}
			try {
				PaymentLookupResult lookup = paymentClient.lookup(candidate.tossOrderId());
				PaymentReconcileOutcome outcome = reconcileService.apply(candidate, lookup);
				if (outcome.needsCompensation()) {
					CompensationResult result = compensator.cancelApproved(candidate.paymentId(),
							outcome.paymentKey(), outcome.approvedAt(), PaymentCompensator.ORDER_INVALIDATED_REASON);
					reconcileService.recordCompensation(candidate, result);
					if (result.canceled()) {
						compensated++;
					}
				}
				if (outcome.needsCancelRetry()) {
					retryCancel(candidate, outcome.paymentKey());
				}
			} catch (RuntimeException e) {
				failed++;
				log.warn("대사 처리 실패 paymentId={}, orderId={}", candidate.paymentId(), candidate.orderId(), e);
				recordFailureSafely(candidate, e);
			}
			processed++;
		}
		log.info("결제 대사 완료 candidates={} processed={} compensated={} failed={}", candidates.size(), processed,
				compensated, failed);
	}

	private void retryCancel(PaymentReconcileCandidate candidate, String paymentKey) {
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(paymentKey, "주문 취소 재시도");
		} catch (BusinessException ex) {
			reconcileService.recordCancelRetry(candidate, null, ex);
			return;
		} catch (RuntimeException ex) {
			log.warn("취소 재시도 결과 불명 paymentId={}, orderId={}", candidate.paymentId(), candidate.orderId(), ex);
			reconcileService.recordCancelRetry(candidate, null,
					new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, ex.getMessage()));
			return;
		}
		reconcileService.recordCancelRetry(candidate, result, null);
	}

	private void recordFailureSafely(PaymentReconcileCandidate candidate, RuntimeException cause) {
		try {
			reconcileService.recordFailure(candidate, cause.getMessage());
		} catch (RuntimeException recordFailureEx) {
			log.error("대사 실패 기록도 실패함 paymentId={}", candidate.paymentId(), recordFailureEx);
			alertNotifier.notify(Alert.critical("payment.reconcile-record-failed",
					"대사 실패 기록도 실패함 paymentId=" + candidate.paymentId(), "paymentId=" + candidate.paymentId()));
		}
	}
}
