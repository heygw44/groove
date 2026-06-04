package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

import java.time.Instant;

/**
 * PG 가 비동기로 전달하는 결제 결과 통보.
 *
 * <p>{@code POST /api/v1/payments/webhook} 요청 본문({@code PaymentWebhookRequest}, API.md §3.6)의 인프로세스
 * 표현이다 — {@code MockWebhookSimulator} 가 이 통보를 {@link WebhookDispatcher}({@code PaymentWebhookHandler})
 * 로 발사하면 서명 검증 후 동일한 {@code PaymentCallbackService} 경로로 처리된다(#W7-4).
 *
 * @param pgTransactionId PG 거래 식별자
 * @param orderNumber     주문 식별자
 * @param status          결제 결과 — {@link PaymentStatus#PAID} 또는 {@link PaymentStatus#FAILED}
 * @param occurredAt      PG 측 처리 시각
 * @param signature       서명 값 (Mock 에서는 단순 공유 시크릿, 실 PG 에서는 PG별 서명) — {@code WebhookSignatureVerifier} 가 검증
 */
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
            // 웹훅은 최종 결제 결과만 통보한다 — PENDING/REFUNDED 는 계약 밖. null 도 여기서 걸린다.
            throw new IllegalArgumentException("webhook status 는 PAID 또는 FAILED 여야 합니다 (현재: " + status + ")");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt 은 null 일 수 없습니다");
        }
    }
}
