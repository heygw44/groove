package com.groove.payment.api.dto;

import com.groove.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * PG 결제 결과 웹훅 요청 본문 (API.md §3.6). 서명은 {@code X-Mock-Signature} 헤더로 받는다.
 *
 * <p>{@code status} 는 최종 결과만 통보한다 — PAID 또는 FAILED 외(PENDING/REFUNDED)는 본문 역직렬화 시점에
 * 거부한다({@link com.groove.payment.gateway.WebhookNotification} 와 동일 규약, HTTP 400). 누락/{@code null}
 * 은 {@code @NotNull} 이 잡는다.
 *
 * @param pgTransactionId PG 거래 식별자 (blank 불가)
 * @param status          결제 결과 — PAID 또는 FAILED
 * @param failureReason   실패 사유 — FAILED 일 때만 의미 (선택, 미상이면 기본 사유가 기록됨)
 * @param occurredAt      PG 측 처리 시각 (선택)
 */
public record PaymentWebhookRequest(
        @NotBlank String pgTransactionId,
        @NotNull PaymentStatus status,
        String failureReason,
        Instant occurredAt) {

    public PaymentWebhookRequest {
        if (status != null && status != PaymentStatus.PAID && status != PaymentStatus.FAILED) {
            throw new IllegalArgumentException("webhook status 는 PAID 또는 FAILED 여야 합니다 (현재: " + status + ")");
        }
    }
}
