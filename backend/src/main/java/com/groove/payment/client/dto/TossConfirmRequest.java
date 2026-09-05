package com.groove.payment.client.dto;

/** 토스 승인 요청 바디. 금액은 원 단위 정수로 보낸다. */
public record TossConfirmRequest(String paymentKey, String orderId, long amount) {
}
