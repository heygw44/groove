package com.groove.payment.gateway.toss.dto;

/** 토스 결제 승인 요청 바디(POST /v1/payments/confirm). */
public record TossConfirmRequest(String paymentKey, String orderId, long amount) {
}
