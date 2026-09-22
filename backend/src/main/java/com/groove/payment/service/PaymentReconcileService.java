package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentReconcileCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentReconcileAction;
import com.groove.payment.entity.PaymentReconcileLog;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentReconcileLogRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대사 후보 조회와 판단 적용. 토스 조회(HTTP)는 {@link com.groove.payment.scheduler.PaymentReconcileScheduler}
 * 가 트랜잭션 밖에서 하고, 이 클래스는 짧은 쓰기 트랜잭션만 맡는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentReconcileService {

	private static final String DONE_TOSS_STATUS = "DONE";
	private static final String PARTIAL_CANCELED_TOSS_STATUS = "PARTIAL_CANCELED";
	private static final String LOOKUP_ERROR_TOSS_STATUS = "LOOKUP_ERROR";

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final PaymentConfirmWriter writer;
	private final PaymentCancelWriter cancelWriter;
	private final PaymentReconcileLogRepository logRepository;
	private final PaymentReconcileProperties properties;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	public List<PaymentReconcileCandidate> findCandidates(LocalDateTime now) {
		LocalDateTime before = now.minus(properties.grace());
		return paymentRepository.findReconcileCandidates(PaymentStatus.RECONCILE_TARGETS, before,
				properties.maxAttempts(),
				Limit.of(properties.batchSize()));
	}

	/** 주문 FOR UPDATE 를 먼저 잡은 뒤 결제를 조회한다. 그 사이 confirm 이 끝냈다면 로그 없이 넘어간다. */
	@Transactional
	public PaymentReconcileOutcome apply(PaymentReconcileCandidate candidate, PaymentLookupResult lookup) {
		return applyInternal(candidate, lookup, false, null);
	}

	/**
	 * 웹훅(PaymentWebhookService) 전용 진입점. FAILED 로 확정된 결제도 대사 대상으로 허용한다 — 재시도
	 * 상한을 넘겨 FAILED 로 확정한 뒤 토스가 뒤늦게 DONE/CANCELED 로 바뀌는 경우를 잡기 위해서다. 서명 없는
	 * 웹훅이 트리거하지만 이 메서드 자체는 항상 lookup() 재조회 결과로만 판단하므로 본문 위조와 무관하다.
	 * 스케줄러가 도는 named lock과는 다른 경로지만, 같은 주문을 겨냥한 confirm/스케줄러 대사와의 경합은
	 * orderRepository.findByIdForUpdate 의 행 락과 Payment.@Version 이 직렬화해 named lock 이 필요 없다.
	 */
	@Transactional
	public PaymentReconcileOutcome applyFromWebhook(PaymentReconcileCandidate candidate, PaymentLookupResult lookup) {
		return applyInternal(candidate, lookup, true, "webhook");
	}

	private PaymentReconcileOutcome applyInternal(PaymentReconcileCandidate candidate, PaymentLookupResult lookup,
			boolean allowFailed, String detail) {
		Order order = orderRepository.findByIdForUpdate(candidate.orderId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(candidate.paymentId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		boolean eligible = payment.getStatus().isReconcileTarget()
				|| (allowFailed && payment.getStatus() == PaymentStatus.FAILED);
		if (!eligible) {
			return PaymentReconcileOutcome.alreadyResolved();
		}

		PaymentStatus beforeStatus = payment.getStatus();
		String tossStatus = lookup.status().name();
		PaymentReconcileDecision decision = PaymentReconcileRule.decide(payment.getStatus(), order.getStatus(),
				payment.getAmount(), lookup);
		return switch (decision) {
			case APPROVE -> {
				writer.approve(candidate.orderId(), candidate.paymentId(), lookup.paymentKey(),
						new PaymentConfirmResult(lookup.paymentKey(), candidate.tossOrderId(), lookup.method(),
								lookup.totalAmount(), lookup.approvedAt()));
				writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.APPROVED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case FAIL -> {
				payment.fail("대사: 토스 " + tossStatus);
				writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.FAILED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case SYNC_CANCELED -> {
				LocalDateTime canceledAt = lookup.canceledAt() != null ? lookup.canceledAt()
						: LocalDateTime.now(clock);
				payment.compensate(lookup.paymentKey(), lookup.approvedAt(), canceledAt, "대사: 토스에서 이미 취소됨");
				writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.CANCELED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case SKIP -> {
				recordMiss(payment, beforeStatus, tossStatus, PaymentReconcileAction.SKIPPED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case MANUAL_REVIEW -> {
				log.error("대사 결과 수동 확인 필요: paymentId={}, orderId={}, tossStatus={}", candidate.paymentId(),
						candidate.orderId(), tossStatus);
				alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
						"대사 결과 수동 확인 필요: paymentId=" + candidate.paymentId() + ", orderId=" + candidate.orderId()
								+ ", tossStatus=" + tossStatus,
						"paymentId=" + candidate.paymentId()));
				recordMiss(payment, beforeStatus, tossStatus, PaymentReconcileAction.MANUAL_REVIEW, detail);
				yield PaymentReconcileOutcome.applied();
			}
			// 토스 cancel 은 트랜잭션 밖에서 호출해야 하므로 여기서는 상태를 바꾸지 않는다.
			case COMPENSATE -> PaymentReconcileOutcome.needsCompensation(lookup.paymentKey(), lookup.approvedAt());
			case COMPLETE_CANCEL -> {
				LocalDateTime canceledAt = lookup.canceledAt() != null ? lookup.canceledAt() : LocalDateTime.now(clock);
				cancelWriter.completeCancel(candidate.orderId(), candidate.paymentId(), canceledAt);
				writeLog(payment, beforeStatus, lookup.status().name(), PaymentReconcileAction.CANCELED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case RETRY_CANCEL -> PaymentReconcileOutcome.needsCancelRetry(lookup.paymentKey());
		};
	}

	@Transactional
	public void recordCancelRetry(PaymentReconcileCandidate candidate, PaymentCancelResult result,
			BusinessException failure) {
		orderRepository.findByIdForUpdate(candidate.orderId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(candidate.paymentId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
			return;
		}
		PaymentStatus beforeStatus = payment.getStatus();
		if (result != null) {
			LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
			cancelWriter.completeCancel(candidate.orderId(), candidate.paymentId(), canceledAt);
			writeLog(payment, beforeStatus, "CANCELED", PaymentReconcileAction.CANCELED, null);
			return;
		}
		if (failure != null && failure.getErrorCode() != ErrorCode.PAYMENT_RESULT_UNKNOWN) {
			cancelWriter.revertCancelRequest(candidate.orderId(), candidate.paymentId());
			log.error("토스 취소 재시도 거절: paymentId={}, orderId={}", candidate.paymentId(), candidate.orderId(), failure);
			alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
					"토스 취소 재시도 거절: paymentId=" + candidate.paymentId() + ", orderId=" + candidate.orderId(),
					"paymentId=" + candidate.paymentId()));
			writeLog(payment, beforeStatus, "DONE", PaymentReconcileAction.MANUAL_REVIEW, "토스가 취소를 거절");
			return;
		}
		recordMiss(payment, beforeStatus, "DONE", PaymentReconcileAction.SKIPPED,
				failure == null ? null : failure.getMessage());
	}

	/** compensator 의 토스 cancel 결과를 반영한다. 성공이면 CANCELED, 실패면 miss 처리 후 SKIPPED 로 남긴다. */
	@Transactional
	public void recordCompensation(PaymentReconcileCandidate candidate, CompensationResult result) {
		recordCompensation(candidate, result, null);
	}

	/** {@link #recordCompensation(PaymentReconcileCandidate, CompensationResult)} 에 로그 detail 태그를 얹는다. */
	@Transactional
	public void recordCompensation(PaymentReconcileCandidate candidate, CompensationResult result, String detail) {
		orderRepository.findByIdForUpdate(candidate.orderId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(candidate.paymentId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		PaymentStatus beforeStatus = payment.getStatus();
		if (result.canceled()) {
			writeLog(payment, beforeStatus, DONE_TOSS_STATUS, PaymentReconcileAction.CANCELED, detail);
			return;
		}
		recordMiss(payment, beforeStatus, DONE_TOSS_STATUS, PaymentReconcileAction.SKIPPED,
				combineDetail(detail, result.failureDetail()));
	}

	/** detail 태그가 실패 사유(failureDetail)를 덮어쓰지 않도록 둘 다 있으면 합친다. */
	private String combineDetail(String detail, String failureDetail) {
		if (detail == null) {
			return failureDetail;
		}
		if (failureDetail == null) {
			return detail;
		}
		return detail + ": " + failureDetail;
	}

	/** 토스 조회·적용 중 예기치 못한 예외. 결제가 아직 unresolved 일 때만 miss 처리한다. */
	@Transactional
	public void recordFailure(PaymentReconcileCandidate candidate, String detail) {
		Payment payment = paymentRepository.findById(candidate.paymentId()).orElse(null);
		if (payment == null || !payment.getStatus().isReconcileTarget()) {
			return;
		}
		recordMiss(payment, payment.getStatus(), LOOKUP_ERROR_TOSS_STATUS, PaymentReconcileAction.SKIPPED, detail);
	}

	/**
	 * 재시도 횟수를 올리고, 상한에 닿으면 관측 이력에 따라 MANUAL_REVIEW(상태 유지) 또는 FAILED(만료 대상으로 확정)로
	 * 수렴시킨다. 라운드당 로그는 한 행만 남기므로 상한 도달 라운드는 defaultAction 대신 확정된 action 을 쓴다.
	 */
	private void recordMiss(Payment payment, PaymentStatus beforeStatus, String tossStatus,
			PaymentReconcileAction defaultAction, String detail) {
		payment.recordReconcileMiss();
		if (payment.getReconcileAttempts() < properties.maxAttempts()) {
			writeLog(payment, beforeStatus, tossStatus, defaultAction, detail);
			return;
		}
		if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
			log.error("취소 대사 상한 도달, 수동 확인 필요: paymentId={}", payment.getId());
			alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
					"취소 대사 상한 도달, 수동 확인 필요: paymentId=" + payment.getId(), "paymentId=" + payment.getId()));
			writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.MANUAL_REVIEW,
					"취소 대사 상한 도달, 수동 확인 필요");
			return;
		}
		boolean observedDoneOrPartial = DONE_TOSS_STATUS.equals(tossStatus)
				|| PARTIAL_CANCELED_TOSS_STATUS.equals(tossStatus)
				|| logRepository.existsByPaymentIdAndTossStatus(payment.getId(), DONE_TOSS_STATUS)
				|| logRepository.existsByPaymentIdAndTossStatus(payment.getId(), PARTIAL_CANCELED_TOSS_STATUS);
		if (observedDoneOrPartial) {
			log.error("대사 상한 도달, 수동 확인 필요: paymentId={}", payment.getId());
			alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
					"대사 상한 도달, 수동 확인 필요: paymentId=" + payment.getId(), "paymentId=" + payment.getId()));
			writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.MANUAL_REVIEW, "대사 상한 도달, 수동 확인 필요");
			return;
		}
		payment.fail("대사 상한 초과");
		writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.FAILED, "대사 상한 초과");
	}

	private void writeLog(Payment payment, PaymentStatus beforeStatus, String tossStatus,
			PaymentReconcileAction action, String detail) {
		logRepository.save(PaymentReconcileLog.of(payment, beforeStatus, tossStatus, action, detail));
	}
}
