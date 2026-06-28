package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

import java.time.Instant;

/** PG 가 비동기로 전달하는 결제 결과 통보. */
public record WebhookNotification(
        String pgTransactionId,
        String orderNumber,
        PaymentStatus status,
        Instant occurredAt,
        String signature
) {

    public WebhookNotification {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber 는 비어 있을 수 없습니다");
        }
        if (status != PaymentStatus.PAID && status != PaymentStatus.FAILED) {
            // null 도 여기서 걸린다.
            throw new IllegalArgumentException("webhook status 는 PAID 또는 FAILED 여야 합니다 (현재: " + status + ")");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt 은 null 일 수 없습니다");
        }
    }
}
