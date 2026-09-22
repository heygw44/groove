package com.groove.payment.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentCompensationCandidate;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.repository.PaymentCompensationRepository;
import com.groove.payment.service.CompensationResult;
import com.groove.payment.service.PaymentCompensationWriter;
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
	private final PaymentCompensationRepository compensationRepository;
	private final PaymentCompensationWriter compensationWriter;
	private final PaymentReconcileProperties reconcileProperties;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;

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
		reconcileCompensations(now);
	}

	/** 기존 대사 후보 처리가 끝난 뒤, 같은 named lock 안에서 payment_compensation 대기 큐를 회수한다. */
	private void reconcileCompensations(LocalDateTime now) {
		LocalDateTime before = now.minus(reconcileProperties.grace());
		List<PaymentCompensationCandidate> candidates = compensationRepository.findCandidates(before,
				reconcileProperties.maxAttempts(), Limit.of(reconcileProperties.batchSize()));
		int processed = 0;
		for (PaymentCompensationCandidate candidate : candidates) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 결제 보상 대기 회수 중단 processed={} remaining={}", processed,
						candidates.size() - processed);
				break;
			}
			try {
				retryCompensation(candidate);
			} catch (RuntimeException ex) {
				log.error("결제 보상 대기 회수 처리 실패 paymentKey={}", candidate.paymentKey(), ex);
			}
			processed++;
		}
		if (processed > 0) {
			log.info("결제 보상 대기 회수 완료 candidates={} processed={}", candidates.size(), processed);
		}
	}

	/**
	 * 토스가 이미 취소된 결제로 응답하면(ALREADY_CANCELED_PAYMENT) PaymentClient 구현체가 이를 취소 성공으로
	 * 흡수해 그대로 완료 처리된다. 그 밖의 명확한 거절은 재시도해도 결과가 바뀌지 않으므로 상한을 기다리지 않고
	 * 즉시 수동 확인으로 넘긴다. 결과 불명은 재시도 횟수만 올린다.
	 */
	private void retryCompensation(PaymentCompensationCandidate candidate) {
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(candidate.paymentKey(), candidate.reason());
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				compensationWriter.fail(candidate.paymentKey(), ex.getMessage());
				return;
			}
			log.error("결제 보상 대기 거절, 수동 확인 필요: paymentKey={}", candidate.paymentKey(), ex);
			compensationWriter.reviewManually(candidate.paymentKey(), ex.getMessage());
			return;
		} catch (RuntimeException ex) {
			log.warn("결제 보상 대기 회수 결과 불명 paymentKey={}", candidate.paymentKey(), ex);
			compensationWriter.fail(candidate.paymentKey(), ex.getMessage());
			return;
		}
		LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
		compensationWriter.complete(candidate.paymentKey(), canceledAt);
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
		}
	}
}
