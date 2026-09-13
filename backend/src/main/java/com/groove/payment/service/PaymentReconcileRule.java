package com.groove.payment.service;

import java.math.BigDecimal;

import com.groove.order.entity.OrderStatus;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;

/**
 * 토스 조회 결과와 DB 상태를 견줘 대사 처리를 결정하는 순수 함수. 호출 시점의 DB 결제는 항상 READY/UNKNOWN
 * 이다(아니면 호출 전에 걸러진다).
 */
public final class PaymentReconcileRule {

	private PaymentReconcileRule() {
	}

	public static PaymentReconcileDecision decide(OrderStatus orderStatus, BigDecimal paymentAmount,
			PaymentLookupResult lookup) {
		PaymentLookupStatus status = lookup.status();
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
