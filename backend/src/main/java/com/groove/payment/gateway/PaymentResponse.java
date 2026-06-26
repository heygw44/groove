package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 결제 요청 즉시 응답. status 는 항상 PENDING.
 * pgTransactionId: PG 발급 거래 식별자. provider: PG 식별자.
 */
public record PaymentResponse(String pgTransactionId, PaymentStatus status, String provider) {

    public PaymentResponse {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (status != PaymentStatus.PENDING) {
            // request() 응답 status 는 항상 PENDING. null 도 여기서 걸린다.
            throw new IllegalArgumentException("request 응답 status 는 PENDING 이어야 합니다 (현재: " + status + ")");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider 는 비어 있을 수 없습니다");
        }
    }
}
