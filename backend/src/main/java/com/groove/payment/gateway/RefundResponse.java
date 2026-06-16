package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

import java.time.Instant;

/**
 * PG 환불 요청에 대한 응답.
 *
 * <p>pgTransactionId: 환불 처리된 거래 식별자. status: 환불 후 상태. refundedAt: 환불 완료 시각.
 */
public record RefundResponse(String pgTransactionId, PaymentStatus status, Instant refundedAt) {

    public RefundResponse {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 는 null 일 수 없습니다");
        }
        if (refundedAt == null) {
            throw new IllegalArgumentException("refundedAt 은 null 일 수 없습니다");
        }
    }
}
