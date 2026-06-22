package com.groove.payment.gateway.toss.dto;

/**
 * 토스 결제 승인 요청 바디 — {@code POST /v1/payments/confirm}.
 *
 * <p>paymentKey: 위젯이 발급한 결제 키. orderId: 가맹점 주문번호. amount: 승인 금액(원).
 */
public record TossConfirmRequest(String paymentKey, String orderId, long amount) {
}
