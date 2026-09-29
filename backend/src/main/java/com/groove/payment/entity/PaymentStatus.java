package com.groove.payment.entity;

import java.util.List;

/**
 * 결제 상태. 승인 전 READY 로 만들어지고 토스 승인/취소 결과에 따라 전이한다.
 * UNKNOWN 은 토스 승인 호출의 처리 결과를 모르는 상태(대사로 수렴), CANCEL_REQUESTED 는 취소를 요청하고 토스 결과를 기다리는 상태다.
 * PARTIAL_CANCELED 는 부분취소로 결제 일부만 남은 상태다(canceled_amount &lt; amount). 잔액 불일치 시 대사 재시도
 * 대상 포함 여부는 후속 과제라 지금은 {@link #RECONCILE_TARGETS}/{@link #SCHEDULED_RECONCILE_TARGETS} 어디에도
 * 넣지 않는다.
 */
public enum PaymentStatus {
	READY, DONE, CANCELED, FAILED, UNKNOWN, CANCEL_REQUESTED, WAITING_FOR_DEPOSIT, PARTIAL_CANCELED;

	/** 토스 결과가 DB 에 아직 확정되지 않은 상태. 대사 대상이고, 이 결제가 걸린 주문은 만료시키지 않는다. */
	public static final List<PaymentStatus> UNRESOLVED = List.of(READY, UNKNOWN);

	/**
	 * 대사 적용(웹훅·만료 재조회 포함) 대상. WAITING_FOR_DEPOSIT 는 UNRESOLVED 에 넣지 않는다 - 넣으면 만료 후보
	 * 조회에서 빠져 입금기한이 지나도 정리되지 않는다.
	 */
	public static final List<PaymentStatus> RECONCILE_TARGETS = List.of(READY, UNKNOWN, CANCEL_REQUESTED,
			WAITING_FOR_DEPOSIT);

	/**
	 * 대사 스케줄러가 주기적으로 조회하는 대상. WAITING_FOR_DEPOSIT 은 빠진다 - SKIP 은 결제 행을 건드리지 않아
	 * updatedAt 순 배치의 맨 앞을 계속 차지하고, 입금기한 내내 매 주기 토스를 조회하게 된다. 입금 확인은 웹훅이,
	 * 웹훅 유실은 입금기한 만료 시점의 재조회가 맡는다.
	 */
	public static final List<PaymentStatus> SCHEDULED_RECONCILE_TARGETS = List.of(READY, UNKNOWN, CANCEL_REQUESTED);

	public boolean isUnresolved() {
		return UNRESOLVED.contains(this);
	}

	public boolean isReconcileTarget() {
		return RECONCILE_TARGETS.contains(this);
	}
}
