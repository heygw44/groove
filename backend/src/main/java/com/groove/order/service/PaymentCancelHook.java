package com.groove.order.service;

import com.groove.order.entity.Order;

/** 결제 완료(PAID) 주문 취소 시 결제 도메인에 알리는 확장 지점. 결제 도메인 도입 전까지는 {@link NoOpPaymentCancelHook} 이 담당한다. */
public interface PaymentCancelHook {

	void onPaidOrderCanceled(Order order);
}
