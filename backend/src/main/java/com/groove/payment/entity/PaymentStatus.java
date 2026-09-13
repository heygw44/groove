package com.groove.payment.entity;

import java.util.List;

/**
 * 결제 상태. 승인 전 READY 로 만들어지고 토스 승인/취소 결과에 따라 전이한다.
 * UNKNOWN 은 토스 승인 호출의 처리 결과를 모르는 상태(대사로 수렴), CANCEL_REQUESTED 는 취소를 요청하고 토스 결과를 기다리는 상태다.
 */
public enum PaymentStatus {
	READY, DONE, CANCELED, FAILED, UNKNOWN, CANCEL_REQUESTED;

	/** 토스 결과가 DB 에 아직 확정되지 않은 상태. 대사 대상이고, 이 결제가 걸린 주문은 만료시키지 않는다. */
	public static final List<PaymentStatus> UNRESOLVED = List.of(READY, UNKNOWN);
	public static final List<PaymentStatus> RECONCILE_TARGETS = List.of(READY, UNKNOWN, CANCEL_REQUESTED);

	public boolean isUnresolved() {
		return UNRESOLVED.contains(this);
	}

	public boolean isReconcileTarget() {
		return RECONCILE_TARGETS.contains(this);
	}
}
