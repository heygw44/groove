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
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
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
	private final LimitedPurchaseRepository limitedPurchaseRepository;

	public List<PaymentReconcileCandidate> findCandidates(LocalDateTime now) {
		LocalDateTime before = now.minus(properties.grace());
		return paymentRepository.findReconcileCandidates(PaymentStatus.SCHEDULED_RECONCILE_TARGETS, before,
				properties.maxAttempts(),
				Limit.of(properties.batchSize()));
	}

	/** 주문 FOR UPDATE 를 먼저 잡은 뒤 결제를 조회한다. 그 사이 confirm 이 끝냈다면 로그 없이 넘어간다. */
	@Transactional
	public PaymentReconcileOutcome apply(PaymentReconcileCandidate candidate, PaymentLookupResult lookup) {
		return applyInternal(candidate, lookup, false, null);
	}

	/**
	 * 웹훅·정산 대사(PaymentLateResultApplier) 공용 진입점. FAILED 로 확정된 결제도 대사 대상으로 허용한다 —
	 * 재시도 상한을 넘겨 FAILED 로 확정한 뒤 토스가 뒤늦게 DONE/CANCELED 로 바뀌는 경우를 잡기 위해서다. 웹훅은
	 * 서명이 없어 본문을 신뢰하지 않지만, 이 메서드 자체는 항상 lookup() 재조회 결과로만 판단하므로 본문 위조와
	 * 무관하다. 스케줄러가 도는 named lock과는 다른 경로지만, 같은 주문을 겨냥한 confirm/스케줄러 대사와의 경합은
	 * orderRepository.findByIdForUpdate 의 행 락과 Payment.@Version 이 직렬화해 named lock 이 필요 없다.
	 */
	@Transactional
	public PaymentReconcileOutcome applyLate(PaymentReconcileCandidate candidate, PaymentLookupResult lookup,
			String detail) {
		return applyInternal(candidate, lookup, true, detail);
	}

	private PaymentReconcileOutcome applyInternal(PaymentReconcileCandidate candidate, PaymentLookupResult lookup,
			boolean allowFailed, String detail) {
		Order order = orderRepository.findByIdForUpdate(candidate.orderId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(candidate.paymentId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		boolean partialCancelDriftCandidate = PaymentReconcileRule.isPartialCancelDriftCandidate(payment.getStatus(),
				lookup.status());
		boolean eligible = payment.getStatus().isReconcileTarget()
				|| (allowFailed && payment.getStatus() == PaymentStatus.FAILED)
				|| partialCancelDriftCandidate;
		if (!eligible) {
			return PaymentReconcileOutcome.alreadyResolved();
		}

		PaymentStatus beforeStatus = payment.getStatus();
		String tossStatus = lookup.status().name();
		PaymentReconcileDecision decision = PaymentReconcileRule.decide(payment.getStatus(), order.getStatus(),
				payment.getAmount(), payment.getCanceledAmount(), lookup);
		return switch (decision) {
			case APPROVE -> {
				writer.approve(candidate.orderId(), candidate.paymentId(), lookup.paymentKey(),
						new PaymentConfirmResult(lookup.paymentKey(), candidate.tossOrderId(), lookup.method(),
								lookup.totalAmount(), lookup.approvedAt(), PaymentLookupStatus.DONE,
								lookup.easyPayProvider(), null));
				writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.APPROVED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case ISSUE_VIRTUAL_ACCOUNT -> {
				if (limitedPurchaseRepository.existsByOrderId(candidate.orderId())) {
					// 한정반은 가상계좌를 받지 않는다. 토스 cancel 은 트랜잭션 밖에서 불러야 해 여기선 아무것도
					// 쓰지 않고 LimitedVirtualAccountCloser 에 폐쇄를 넘긴다(결과는 recordLimitedVirtualAccountClose).
					yield PaymentReconcileOutcome.needsVirtualAccountClose(lookup.paymentKey());
				}
				writer.issueVirtualAccount(candidate.orderId(), candidate.paymentId(), lookup.paymentKey(),
						new PaymentConfirmResult(lookup.paymentKey(), candidate.tossOrderId(), lookup.method(),
								lookup.totalAmount(), lookup.approvedAt(), PaymentLookupStatus.WAITING_FOR_DEPOSIT,
								lookup.easyPayProvider(), lookup.virtualAccount()));
				writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.ISSUED, detail);
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
				String reason = "대사: 토스에서 이미 취소됨";
				if (payment.getStatus() == PaymentStatus.WAITING_FOR_DEPOSIT && lookup.approvedAt() == null) {
					// 입금 전 가상계좌 폐쇄는 돈이 오간 적이 없어 취소 기록을 남기지 않는다.
					payment.cancelVirtualAccount(reason, canceledAt);
				} else {
					writer.compensateWithCancelRecord(payment, lookup.paymentKey(), lookup.approvedAt(), canceledAt,
							reason, lookup.lastCancelTransactionKey());
				}
				writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.CANCELED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case SKIP -> {
				if (partialCancelDriftCandidate || payment.getStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
					// 부분취소 잔액 대사는 이미 확정된 결제(DONE/PARTIAL_CANCELED)를 건드리는 것이라 재시도
					// 상한(recordMiss)을 태우지 않는다 - 상한을 넘기면 이 결제가 fail() 로 되돌아갈 수 있다.
					// 입금기한이 남아있는 정상 대기 상태도 마찬가지로 상한 없이 SKIPPED 만 남긴다 - 대사
					// 상한을 적용하면 며칠씩 걸리는 입금 대기가 몇 번 폴링만에 FAILED 로 잘못 수렴한다(만료
					// 처리는 OrderExpirationService 가 맡는다).
					writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.SKIPPED, detail);
				} else {
					recordMiss(payment, beforeStatus, tossStatus, PaymentReconcileAction.SKIPPED, detail);
				}
				yield PaymentReconcileOutcome.applied();
			}
			case MANUAL_REVIEW -> manualReview(candidate, payment, beforeStatus, tossStatus, detail,
					partialCancelDriftCandidate);
			// 토스 cancel 은 트랜잭션 밖에서 호출해야 하므로 여기서는 상태를 바꾸지 않는다.
			case COMPENSATE -> PaymentReconcileOutcome.needsCompensation(lookup.paymentKey(), lookup.approvedAt());
			case COMPLETE_CANCEL -> {
				LocalDateTime canceledAt = lookup.canceledAt() != null ? lookup.canceledAt() : LocalDateTime.now(clock);
				// 토스 조회(lookup)에는 취소 거래의 transactionKey 가 없어 null 로 남긴다.
				cancelWriter.completeCancel(candidate.orderId(), candidate.paymentId(), canceledAt, null);
				writeLog(payment, beforeStatus, lookup.status().name(), PaymentReconcileAction.CANCELED, detail);
				yield PaymentReconcileOutcome.applied();
			}
			case RETRY_CANCEL -> PaymentReconcileOutcome.needsCancelRetry(lookup.paymentKey(),
					cancelWriter.requestedIdempotencyKey(payment), cancelWriter.requestedRefundAccount(payment));
		};
	}

	private PaymentReconcileOutcome manualReview(PaymentReconcileCandidate candidate, Payment payment,
			PaymentStatus beforeStatus, String tossStatus, String detail, boolean partialCancelDriftCandidate) {
		log.error("대사 결과 수동 확인 필요: paymentId={}, orderId={}, tossStatus={}", candidate.paymentId(),
				candidate.orderId(), tossStatus);
		alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
				"대사 결과 수동 확인 필요: paymentId=" + candidate.paymentId() + ", orderId=" + candidate.orderId()
						+ ", tossStatus=" + tossStatus,
				"paymentId=" + candidate.paymentId()));
		if (partialCancelDriftCandidate) {
			// 자동으로 금액을 맞추지 않는다 - 상한(recordMiss)을 태우면 이미 확정된 결제가 fail() 로
			// 되돌아갈 수 있어, 로그만 남기고 payment.status·reconcile_attempts 는 건드리지 않는다.
			writeLog(payment, beforeStatus, tossStatus, PaymentReconcileAction.MANUAL_REVIEW, detail);
		} else {
			recordMiss(payment, beforeStatus, tossStatus, PaymentReconcileAction.MANUAL_REVIEW, detail);
		}
		return PaymentReconcileOutcome.applied();
	}

	/**
	 * LimitedVirtualAccountCloser 의 토스 cancel 결과를 반영한다. failure 가 null 이면 폐쇄 성공이다. 성공 시 결제를
	 * FAILED 로 확정만 한다 - 주문은 결제가 unresolved 가 아니게 되면 주문 만료 스케줄러가 만료시키며 재고·한정
	 * 슬롯을 복구한다(만료 전엔 카드로 재결제 가능, confirm 경로와 동일). 입금된 적이 없어 취소 기록은 남기지 않는다.
	 * READY/UNKNOWN 결제의 폐쇄 실패는 FAILED 로 수렴시키지 않는다 - 계좌가 열린 채 한정 슬롯이 풀리기 때문이다.
	 * 대신 결과 불명(PAYMENT_RESULT_UNKNOWN)이든 토스 거절이든 매번 횟수를 센다 - attempts·updated_at 이 갱신돼야
	 * 후보가 배치 뒤로 밀려 같은 결제가 배치 앞을 계속 차지하지 않는다(head-of-line starvation 방지).
	 * 상한에 닿으면 상태를 유지한 채 MANUAL_REVIEW 로 사람에게 넘긴다 - 토스는 입금기한이 지나도
	 * WAITING_FOR_DEPOSIT 을 유지(웹훅 없음)해 자동 수렴이 없다. READY/UNKNOWN 은 대사 대상이라 웹훅·정산 지연 경로는
	 * 상한 후에도 재시도할 수 있다.
	 */
	@Transactional
	public void recordLimitedVirtualAccountClose(PaymentReconcileCandidate candidate, BusinessException failure,
			String detail) {
		orderRepository.findByIdForUpdate(candidate.orderId())
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(candidate.paymentId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		PaymentStatus beforeStatus = payment.getStatus();
		boolean failed = beforeStatus == PaymentStatus.FAILED;
		if (!failed && beforeStatus != PaymentStatus.READY && beforeStatus != PaymentStatus.UNKNOWN) {
			// 토스 호출 사이에 다른 경로가 결제를 정리했다.
			return;
		}
		if (failure == null) {
			if (!failed) {
				payment.fail(LimitedVirtualAccountCloser.REASON);
			}
			writeLog(payment, beforeStatus, "CANCELED",
					failed ? PaymentReconcileAction.CANCELED : PaymentReconcileAction.FAILED, detail);
			return;
		}
		String failureDetail = combineDetail(detail, failure.getMessage());
		if (failed) {
			alertCloseFailure(candidate, failure);
			writeLog(payment, beforeStatus, "WAITING_FOR_DEPOSIT", PaymentReconcileAction.MANUAL_REVIEW, failureDetail);
			return;
		}
		// 결과 불명도 횟수를 센다 - 행이 갱신되지 않으면 updated_at 순 후보 조회가 매번 이 결제를 먼저 집어 뒤 후보가 굶는다.
		payment.recordReconcileMiss();
		if (payment.getReconcileAttempts() < properties.maxAttempts()) {
			PaymentReconcileAction action = failure.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN
					? PaymentReconcileAction.SKIPPED
					: PaymentReconcileAction.MANUAL_REVIEW;
			alertCloseFailure(candidate, failure);
			writeLog(payment, beforeStatus, "WAITING_FOR_DEPOSIT", action, failureDetail);
			return;
		}
		// 상한에서도 fail() 하지 않는다 - FAILED 면 주문 만료가 한정 슬롯을 풀어 열린 계좌로 입금이 들어올 수 있다.
		// 같은 키 알림은 5분 스로틀이라 일반 실패 알림 대신 상한 알림 하나만 보낸다.
		log.error("한정반 가상계좌 폐쇄 상한 도달, 수동 폐쇄 필요: paymentId={}, orderId={}", candidate.paymentId(),
				candidate.orderId(), failure);
		alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
				"한정반 가상계좌 폐쇄 상한 도달, 수동 폐쇄 필요: paymentId=" + candidate.paymentId(),
				"paymentId=" + candidate.paymentId()));
		writeLog(payment, beforeStatus, "WAITING_FOR_DEPOSIT", PaymentReconcileAction.MANUAL_REVIEW,
				"한정반 가상계좌 폐쇄 상한 도달, 수동 폐쇄 필요");
	}

	private void alertCloseFailure(PaymentReconcileCandidate candidate, BusinessException failure) {
		log.error("한정반 가상계좌 폐쇄 실패: paymentId={}, orderId={}", candidate.paymentId(), candidate.orderId(),
				failure);
		alertNotifier.notify(Alert.critical("payment.reconcile-manual-review",
				"한정반 가상계좌 폐쇄 실패: paymentId=" + candidate.paymentId() + ", orderId=" + candidate.orderId(),
				"paymentId=" + candidate.paymentId()));
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
			cancelWriter.completeCancel(candidate.orderId(), candidate.paymentId(), canceledAt,
					result.transactionKey());
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
		// 입금대기는 재시도 상한으로 수렴시키지 않는다 - 상한에 닿으면 입금기한이 남았는데도 FAILED 가 된다.
		if (payment.getStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
			log.warn("입금대기 결제 대사 조회 실패, 만료 시점 재조회에 맡김: paymentId={}, detail={}", payment.getId(), detail);
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
