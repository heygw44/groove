package com.groove.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.config.PaymentReconcileProperties;
import com.groove.payment.dto.PaymentCancelRetryCandidate;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentReconcileAction;
import com.groove.payment.entity.PaymentReconcileLog;
import com.groove.payment.repository.PaymentReconcileLogRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결과불명으로 REQUESTED 에 남은 부분취소(payment_cancel) 한 건을 회수한다. 대사 스케줄러
 * (PaymentReconcileScheduler)가 결제 대사 named lock 안에서 매 주기 이 클래스를 호출한다.
 *
 * <p>재시도는 {@link PaymentRefundWriter}의 T1이 남긴 idempotencyKey 를 그대로 재사용한다 - 토스는 API 키·
 * URL·메서드가 같으면 본문과 무관하게 첫 응답을 그대로 재생하므로 환불계좌 등 부가 정보를 다시 채우지 않아도
 * 안전하다(다만 첫 시도가 토스에 닿기 전에 통신 자체가 끊겼던 가상계좌 환불은 이 재시도가 refundAccount 없이
 * 나가 새 요청으로 처리될 수 있다 - 드문 경우라 지금은 감수한다).</p>
 *
 * <p>payment_cancel 에는 재시도 횟수 컬럼을 두지 않고 requestedAt 로부터 지난 시간으로 판단한다
 * ({@link PaymentReconcileProperties#refundVerifyAfter()}) - 그 안쪽은 같은 키로 재호출하고, 지나면 재호출
 * 대신 토스 결제 조회로 세 갈래를 가른다: ① 우리 취소가 반영된 잔액이면 DONE, ② 반영되지 않은 잔액(다른 변화
 * 없음)이면 재호출을 이미 멈췄으니 늦게 적용될 위험 없이 FAILED 로 닫아 {@code PAYMENT_CANCEL_IN_PROGRESS}
 * 가드를 풀어준다, ③ 그 외(다른 경로 취소가 섞인 드리프트 등 판단 불가)면 payment_cancel 행을 REQUESTED 로
 * 남긴 채(진행 중인 취소로 계속 취급해야 새 취소 요청을 계속 막을 수 있다) {@link PaymentReconcileLog}에
 * MANUAL_REVIEW 표식만 남긴다 - 같은 결제에 이미 그 표식이 있으면 더 조회하지 않는다. 조회 자체가
 * 결과불명(타임아웃·5xx)이면 아무것도 하지 않고 다음 주기로 넘긴다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancelRetrier {

	/** 부분취소 재시도 확인 로그의 표식. 실제 토스 상태 문자열과 겹치지 않아 dedup 조건으로 쓸 수 있다. */
	static final String MANUAL_REVIEW_MARKER = "PARTIAL_CANCEL_RETRY_MANUAL_REVIEW";

	/** 토스 멱등키 유효기간(15일)보다 하루 여유를 두고 재호출을 끊는다 - 설정을 아무리 늘려도 이 시점부터는
	 * 조회로만 확인한다. */
	private static final Duration IDEMPOTENCY_KEY_HARD_STOP = Duration.ofDays(14);

	private final PaymentRefundWriter refundWriter;
	private final PaymentRepository paymentRepository;
	private final PaymentReconcileLogRepository reconcileLogRepository;
	private final PaymentClient paymentClient;
	private final PaymentReconcileProperties properties;
	private final Clock clock;
	private final AlertNotifier alertNotifier;
	private final OrderClaimFinalizeService orderClaimFinalizeService;

	public void retry(PaymentCancelRetryCandidate candidate) {
		LocalDateTime now = LocalDateTime.now(clock);
		Duration age = Duration.between(candidate.requestedAt(), now);
		if (age.compareTo(properties.refundVerifyAfter()) < 0 && age.compareTo(IDEMPOTENCY_KEY_HARD_STOP) < 0) {
			retryCancelCall(candidate);
			return;
		}
		verifyByLookup(candidate);
	}

	private void retryCancelCall(PaymentCancelRetryCandidate candidate) {
		PaymentCancelCommand command = PaymentCancelCommand.of(candidate.paymentKey(), candidate.reason(),
				candidate.cancelAmount(), candidate.idempotencyKey(), null);
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(command);
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				log.info("부분취소 재시도 결과 여전히 불명: paymentCancelId={}", candidate.paymentCancelId());
				return;
			}
			log.warn("부분취소 재시도 거절: paymentCancelId={}", candidate.paymentCancelId(), ex);
			safeFailRefund(candidate);
			return;
		} catch (RuntimeException ex) {
			log.warn("부분취소 재시도 통신 실패, 결과 불명: paymentCancelId={}", candidate.paymentCancelId(), ex);
			return;
		}
		LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
		try {
			refundWriter.completeRefund(candidate.paymentId(), candidate.paymentCancelId(), candidate.cancelAmount(),
					result.transactionKey(), canceledAt);
		} catch (RuntimeException ex) {
			log.error("부분취소 재시도는 성공했으나 반영 실패, 다음 주기로 넘김: paymentCancelId={}", candidate.paymentCancelId(), ex);
		}
	}

	private void safeFailRefund(PaymentCancelRetryCandidate candidate) {
		try {
			refundWriter.failRefund(candidate.paymentCancelId());
		} catch (RuntimeException ex) {
			log.error("부분취소 거절 기록 실패: paymentCancelId={}", candidate.paymentCancelId(), ex);
			return;
		}
		finalizeClaimFailed(candidate);
	}

	private void verifyByLookup(PaymentCancelRetryCandidate candidate) {
		if (reconcileLogRepository.existsByPaymentIdAndTossStatus(candidate.paymentId(), MANUAL_REVIEW_MARKER)) {
			return;
		}
		PaymentLookupResult lookup;
		try {
			lookup = paymentClient.lookup(candidate.tossOrderId());
		} catch (RuntimeException ex) {
			log.warn("부분취소 확인 조회 실패: paymentCancelId={}", candidate.paymentCancelId(), ex);
			return;
		}
		Payment payment = paymentRepository.findById(candidate.paymentId()).orElse(null);
		if (payment == null) {
			return;
		}
		BigDecimal balanceAmount = lookup.balanceAmount();
		if (balanceAmount == null) {
			markManualReview(candidate, payment);
			return;
		}
		BigDecimal remainingBeforeThisCancel = payment.getAmount().subtract(payment.getCanceledAmount());
		BigDecimal remainingIfApplied = remainingBeforeThisCancel.subtract(candidate.cancelAmount());
		if (remainingIfApplied.compareTo(balanceAmount) == 0) {
			completeFromLookup(candidate, lookup);
			return;
		}
		if (remainingBeforeThisCancel.compareTo(balanceAmount) == 0) {
			failNotApplied(candidate);
			return;
		}
		markManualReview(candidate, payment);
	}

	/**
	 * 이 취소가 토스에 반영되지 않은 잔액을 확인했을 때 닫는다. 재호출은 이미 멈췄으므로(age가 재호출 구간을
	 * 지났음) 이 판정 이후 같은 idempotencyKey 로 뒤늦게 적용될 위험이 없다 - FAILED 로 확정해도 안전하다.
	 */
	private void failNotApplied(PaymentCancelRetryCandidate candidate) {
		log.warn("부분취소 재시도 상한 초과, 토스에 반영되지 않아 FAILED 로 닫음: paymentCancelId={}", candidate.paymentCancelId());
		try {
			refundWriter.failRefund(candidate.paymentCancelId());
		} catch (RuntimeException ex) {
			log.error("부분취소 미반영 확정 실패: paymentCancelId={}", candidate.paymentCancelId(), ex);
			return;
		}
		finalizeClaimFailed(candidate);
	}

	private void completeFromLookup(PaymentCancelRetryCandidate candidate, PaymentLookupResult lookup) {
		LocalDateTime canceledAt = lookup.canceledAt() != null ? lookup.canceledAt() : LocalDateTime.now(clock);
		try {
			refundWriter.completeRefund(candidate.paymentId(), candidate.paymentCancelId(), candidate.cancelAmount(),
					lookup.lastCancelTransactionKey(), canceledAt);
		} catch (RuntimeException ex) {
			log.error("부분취소 확인 조회로 완료를 반영하지 못함: paymentCancelId={}", candidate.paymentCancelId(), ex);
		}
	}

	/** 클레임 승인으로 시작된 취소만 되돌릴 대상이다(orderClaimId 가 없으면 전액취소 등 클레임과 무관한 취소). */
	private void finalizeClaimFailed(PaymentCancelRetryCandidate candidate) {
		if (candidate.orderClaimId() == null) {
			return;
		}
		try {
			orderClaimFinalizeService.applyRefundFailed(candidate.orderClaimId());
		} catch (RuntimeException ex) {
			log.error("대사 거절 후 클레임 되돌리기 실패: orderClaimId={}, paymentCancelId={}", candidate.orderClaimId(),
					candidate.paymentCancelId(), ex);
		}
	}

	private void markManualReview(PaymentCancelRetryCandidate candidate, Payment payment) {
		log.error("부분취소 결과 확인 불가, 수동 확인 필요: paymentCancelId={}, paymentId={}", candidate.paymentCancelId(),
				candidate.paymentId());
		alertNotifier.notify(Alert.critical("payment.refund-manual-review",
				"부분취소 결과 확인 불가, 수동 확인 필요: paymentCancelId=" + candidate.paymentCancelId() + ", paymentId="
						+ candidate.paymentId(),
				"paymentId=" + candidate.paymentId()));
		reconcileLogRepository.save(PaymentReconcileLog.of(payment, payment.getStatus(), MANUAL_REVIEW_MARKER,
				PaymentReconcileAction.MANUAL_REVIEW,
				"부분취소 재시도 상한 초과, 조회로 확인 불가: paymentCancelId=" + candidate.paymentCancelId()));
	}
}
