package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

import java.time.Instant;

/** PG 환불 응답. status 는 REFUNDED/PARTIALLY_REFUNDED. */
public record RefundResponse(String pgTransactionId, PaymentStatus status, Instant refundedAt) {

    public RefundResponse {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 는 null 일 수 없습니다");
        }
        if (status != PaymentStatus.REFUNDED && status != PaymentStatus.PARTIALLY_REFUNDED) {
            // PG 가 비환불 상태를 돌려주면 계약 위반으로 거부한다.
            throw new IllegalArgumentException(
                    "환불 응답 status 는 REFUNDED 또는 PARTIALLY_REFUNDED 여야 합니다 (현재: " + status + ")");
        }
        if (refundedAt == null) {
            throw new IllegalArgumentException("refundedAt 은 null 일 수 없습니다");
        }
    }
}
