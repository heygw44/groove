package com.groove.payment.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentCancelRetryCandidate;
import com.groove.payment.dto.PaymentCompensationCandidate;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentCompensationRepository;
import com.groove.payment.service.CompensationResult;
import com.groove.payment.service.LimitedVirtualAccountCloser;
import com.groove.payment.service.PaymentCancelRetrier;
import com.groove.payment.service.PaymentCompensationRetrier;
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

	// 후보마다 토스 HTTP 를 타고 대사 named lock 을 쥔 채 돌므로, 한 회차 재시도를 batchSize×10 건으로 묶는다.
	// 이보다 많은 행이 REQUESTED 로 밀려 있으면 이미 수동 확인 알림이 나간 장애 상황이다.
	private static final int MAX_REFUND_RETRY_ROUNDS = 10;

	// 첫 바퀴는 nullable 파라미터 대신 모든 행보다 앞선 센티널 커서로 조회한다.
	private static final LocalDateTime INITIAL_CURSOR_REQUESTED_AT = LocalDateTime.of(1970, 1, 1, 0, 0);
	private static final long INITIAL_CURSOR_ID = 0L;

	private final PaymentReconcileService reconcileService;
	private final PaymentReconcileLock reconcileLock;
	private final PaymentClient paymentClient;
	private final PaymentCompensator compensator;
	private final PaymentCompensationRepository compensationRepository;
	private final PaymentCompensationRetrier compensationRetrier;
	private final PaymentCancelRepository paymentCancelRepository;
	private final PaymentCancelRetrier paymentCancelRetrier;
	private final PaymentReconcileProperties reconcileProperties;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;
	private final AlertNotifier alertNotifier;
	private final LimitedVirtualAccountCloser limitedVirtualAccountCloser;

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
					retryCancel(candidate, outcome.paymentKey(), outcome.idempotencyKey(),
							outcome.refundAccount());
				}
				if (outcome.needsVirtualAccountClose()) {
					limitedVirtualAccountCloser.close(candidate, outcome.paymentKey(), null);
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
		reconcileRefundRetries(now);
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
				compensationRetrier.retry(candidate);
			} catch (RuntimeException ex) {
				log.error("결제 보상 대기 회수 처리 실패 paymentKey={}", candidate.paymentKey(), ex);
			}
			processed++;
		}
		if (processed > 0) {
			log.info("결제 보상 대기 회수 완료 candidates={} processed={}", candidates.size(), processed);
		}
	}

	/** payment_compensation 회수 다음, 같은 named lock 안에서 결과불명 부분취소(payment_cancel)를 회수한다. */
	private void reconcileRefundRetries(LocalDateTime now) {
		LocalDateTime retryBefore = now.minus(reconcileProperties.refundRetryGrace());
		LocalDateTime afterRequestedAt = INITIAL_CURSOR_REQUESTED_AT;
		long afterId = INITIAL_CURSOR_ID;
		int candidateTotal = 0;
		int processed = 0;
		for (int round = 0; round < MAX_REFUND_RETRY_ROUNDS; round++) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 부분취소 재시도 회수 중단 processed={}", processed);
				break;
			}
			List<PaymentCancelRetryCandidate> candidates = paymentCancelRepository.findRetryCandidates(retryBefore,
					afterRequestedAt, afterId, Limit.of(reconcileProperties.batchSize()));
			if (candidates.isEmpty()) {
				break;
			}
			candidateTotal += candidates.size();
			processed += retryRefundRound(candidates);
			// 수동 확인 대상·결과불명 행은 REQUESTED 로 남아 커서 없이 다시 조회하면 매번 앞자리를 차지해 뒤 정상 건을
			// 굶긴다. 이번 바퀴 후보는 모두 시도했으니 마지막 후보 뒤로 커서를 옮긴다.
			PaymentCancelRetryCandidate last = candidates.get(candidates.size() - 1);
			afterRequestedAt = last.requestedAt();
			afterId = last.paymentCancelId();
			if (candidates.size() < reconcileProperties.batchSize()) {
				break;
			}
		}
		if (processed > 0) {
			log.info("부분취소 재시도 회수 완료 candidates={} processed={}", candidateTotal, processed);
		}
	}

	private int retryRefundRound(List<PaymentCancelRetryCandidate> candidates) {
		int processed = 0;
		for (PaymentCancelRetryCandidate candidate : candidates) {
			if (shutdownSignal.isShuttingDown()) {
				break;
			}
			try {
				paymentCancelRetrier.retry(candidate);
			} catch (RuntimeException ex) {
				log.error("부분취소 재시도 회수 처리 실패 paymentCancelId={}", candidate.paymentCancelId(), ex);
			}
			processed++;
		}
		return processed;
	}

	private void retryCancel(PaymentReconcileCandidate candidate, String paymentKey, String idempotencyKey,
			RefundAccountInfo refundAccount) {
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(
					PaymentCancelCommand.of(paymentKey, "주문 취소 재시도", null, idempotencyKey,
					refundAccount));
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
