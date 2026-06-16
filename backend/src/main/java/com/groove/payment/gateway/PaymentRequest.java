package com.groove.payment.gateway;

/** PG 결제 요청 파라미터 (orderNumber, amount). */
public record PaymentRequest(String orderNumber, long amount) {

    public PaymentRequest {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber 는 비어 있을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다 (현재: " + amount + ")");
        }
    }
}
