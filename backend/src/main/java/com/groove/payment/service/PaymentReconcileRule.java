package com.groove.payment.service;

import java.math.BigDecimal;

import com.groove.order.entity.OrderStatus;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.entity.PaymentStatus;

/**
 * 토스 조회 결과와 DB 상태를 견줘 대사 처리를 결정하는 순수 함수.
 */
public final class PaymentReconcileRule {

	private PaymentReconcileRule() {
	}

	public static PaymentReconcileDecision decide(PaymentStatus paymentStatus, OrderStatus orderStatus,
			BigDecimal paymentAmount, BigDecimal canceledAmount, PaymentLookupResult lookup) {
		if (isPartialCancelDriftCandidate(paymentStatus, lookup.status())) {
			return decidePartialCancelDrift(paymentAmount, canceledAmount, lookup.balanceAmount());
		}
		if (paymentStatus == PaymentStatus.CANCEL_REQUESTED) {
			return decideCancelRequested(orderStatus, lookup.status());
		}
		PaymentLookupStatus status = lookup.status();
		if (status == PaymentLookupStatus.WAITING_FOR_DEPOSIT && isVirtualAccountIssuable(paymentStatus, orderStatus)
				&& hasVirtualAccountSecret(lookup)) {
			return decideIssueVirtualAccount(paymentAmount, lookup);
		}
		if (status == PaymentLookupStatus.DONE) {
			return decideForDone(orderStatus, paymentAmount, lookup);
		}
		if (isTerminalFailure(status)) {
			return PaymentReconcileDecision.FAIL;
		}
		if (isInProgress(status)) {
			return PaymentReconcileDecision.SKIP;
		}
		if (status == PaymentLookupStatus.CANCELED) {
			return PaymentReconcileDecision.SYNC_CANCELED;
		}
		// PARTIAL_CANCELED
		return PaymentReconcileDecision.MANUAL_REVIEW;
	}

	/**
	 * 우리 DB 는 DONE/PARTIAL_CANCELED(이미 확정된 상태)인데 토스는 PARTIAL_CANCELED/CANCELED 를 보고할 때다 -
	 * 우리가 모르는 취소가 토스 쪽에서 일어났거나, 진행 중인 부분취소(payment_cancel REQUESTED)가 아직 반영되지
	 * 않은 것이다. 이 조합만 다른 분기보다 먼저 판정한다 - 그 아래 분기들(SYNC_CANCELED, decideForDone 등)은
	 * 이 상태 조합을 염두에 두지 않고 짜여 있어 그대로 타면 이미 확정된 결제를 잘못 되돌릴 수 있다.
	 */
	public static boolean isPartialCancelDriftCandidate(PaymentStatus paymentStatus, PaymentLookupStatus lookupStatus) {
		boolean ourStatusEligible = paymentStatus == PaymentStatus.DONE
				|| paymentStatus == PaymentStatus.PARTIAL_CANCELED;
		boolean tossStatusEligible = lookupStatus == PaymentLookupStatus.PARTIAL_CANCELED
				|| lookupStatus == PaymentLookupStatus.CANCELED;
		return ourStatusEligible && tossStatusEligible;
	}

	/** 자동으로 금액을 맞추지 않는다 - 진행 중인 부분취소가 있으면 PaymentCancelRetrier 가 처리할 몫이라 SKIP 한다. */
	private static PaymentReconcileDecision decidePartialCancelDrift(BigDecimal paymentAmount,
			BigDecimal canceledAmount, BigDecimal tossBalanceAmount) {
		if (partialCancelBalanceMatches(paymentAmount, canceledAmount, tossBalanceAmount)) {
			return PaymentReconcileDecision.SKIP;
		}
		return PaymentReconcileDecision.MANUAL_REVIEW;
	}

	/** 우리 남은 결제 금액(amount - canceledAmount)과 토스 취소 잔액(balanceAmount)이 일치하는지. 정산 대사
	 * (PaymentSettlementService)도 같은 판정을 재사용한다. */
	public static boolean partialCancelBalanceMatches(BigDecimal paymentAmount, BigDecimal canceledAmount,
			BigDecimal tossBalanceAmount) {
		if (tossBalanceAmount == null) {
			return false;
		}
		BigDecimal expectedBalance = paymentAmount.subtract(canceledAmount);
		return expectedBalance.compareTo(tossBalanceAmount) == 0;
	}

	/** 가상계좌 발급 응답을 못 받아 우리 쪽이 아직 입금대기로 넘어가지 못한 결제다. */
	private static boolean isVirtualAccountIssuable(PaymentStatus paymentStatus, OrderStatus orderStatus) {
		boolean ourStatusEligible = paymentStatus == PaymentStatus.READY || paymentStatus == PaymentStatus.UNKNOWN
				|| paymentStatus == PaymentStatus.FAILED;
		return ourStatusEligible && orderStatus == OrderStatus.PENDING;
	}

	private static boolean hasVirtualAccountSecret(PaymentLookupResult lookup) {
		return lookup.virtualAccount() != null && lookup.virtualAccount().secret() != null
				&& !lookup.virtualAccount().secret().isBlank();
	}

	private static PaymentReconcileDecision decideIssueVirtualAccount(BigDecimal paymentAmount,
			PaymentLookupResult lookup) {
		if (lookup.totalAmount() == null || paymentAmount.compareTo(lookup.totalAmount()) != 0) {
			return PaymentReconcileDecision.MANUAL_REVIEW;
		}
		return PaymentReconcileDecision.ISSUE_VIRTUAL_ACCOUNT;
	}

	private static PaymentReconcileDecision decideCancelRequested(OrderStatus orderStatus,
			PaymentLookupStatus lookupStatus) {
		if (orderStatus != OrderStatus.PAID) {
			return PaymentReconcileDecision.MANUAL_REVIEW;
		}
		if (lookupStatus == PaymentLookupStatus.CANCELED) {
			return PaymentReconcileDecision.COMPLETE_CANCEL;
		}
		if (lookupStatus == PaymentLookupStatus.DONE) {
			return PaymentReconcileDecision.RETRY_CANCEL;
		}
		return PaymentReconcileDecision.MANUAL_REVIEW;
	}

	// 금액 불일치는 주문 상태와 무관하게 가장 먼저 판정한다.
	private static PaymentReconcileDecision decideForDone(OrderStatus orderStatus, BigDecimal paymentAmount,
			PaymentLookupResult lookup) {
		if (lookup.totalAmount() == null || paymentAmount.compareTo(lookup.totalAmount()) != 0) {
			return PaymentReconcileDecision.MANUAL_REVIEW;
		}
		if (orderStatus == OrderStatus.PENDING) {
			return PaymentReconcileDecision.APPROVE;
		}
		if (orderStatus == OrderStatus.CANCELED) {
			return PaymentReconcileDecision.COMPENSATE;
		}
		return PaymentReconcileDecision.MANUAL_REVIEW;
	}

	private static boolean isTerminalFailure(PaymentLookupStatus status) {
		return status == PaymentLookupStatus.NOT_FOUND || status == PaymentLookupStatus.ABORTED
				|| status == PaymentLookupStatus.EXPIRED;
	}

	private static boolean isInProgress(PaymentLookupStatus status) {
		return status == PaymentLookupStatus.READY || status == PaymentLookupStatus.IN_PROGRESS
				|| status == PaymentLookupStatus.WAITING_FOR_DEPOSIT;
	}
}
