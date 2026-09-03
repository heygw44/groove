package com.groove.order.service;

import org.springframework.stereotype.Component;

import com.groove.order.entity.Order;

import lombok.extern.slf4j.Slf4j;

/** 결제 도메인 도입 전 기본 구현. 로그만 남기고 실제 결제 취소는 수행하지 않는다. */
@Slf4j
@Component
public class NoOpPaymentCancelHook implements PaymentCancelHook {

	@Override
	public void onPaidOrderCanceled(Order order) {
		log.info("결제 취소 연동 대상 주문(결제 도메인 미도입): orderId={}, orderNumber={}", order.getId(), order.getOrderNumber());
	}
}
