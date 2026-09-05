package com.groove.order.service;

import com.groove.order.entity.Order;

/**
 * 결제 완료(PAID) 주문 취소 시 결제 도메인에 알리는 확장 지점. 주문 도메인이 결제 도메인을 직접 참조하지 않도록
 * 여기서 의존 방향을 뒤집고, 결제 도메인의 {@code PaymentCancelService} 가 구현한다.
 */
public interface PaymentCancelHook {

	void onPaidOrderCanceled(Order order);
}
