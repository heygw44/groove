package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 결제 요청에 대한 즉시 응답.
 *
 * <p>실 PG 와 마찬가지로 {@code request()} 직후에는 결제가 확정되지 않은 {@link PaymentStatus#PENDING}
 * 상태로 응답하며, 최종 성공/실패는 비동기 웹훅 콜백({@link WebhookNotification}) 으로 전달된다.
 *
 * @param pgTransactionId PG 가 발급한 거래 식별자 (웹훅·조회·환불 시 키)
 * @param status          요청 시점 상태 — 항상 {@link PaymentStatus#PENDING}
 * @param provider        PG 식별자 (예: {@code MOCK})
 */
public record PaymentResponse(String pgTransactionId, PaymentStatus status, String provider) {

    public PaymentResponse {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 는 null 일 수 없습니다");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider 는 비어 있을 수 없습니다");
        }
    }
}
