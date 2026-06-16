package com.groove.payment.api.dto;

import com.groove.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * PG 결제 결과 웹훅 요청 본문. 서명은 X-Mock-Signature 헤더로 받는다.
 * status 는 PAID/FAILED 만 허용하고 그 외는 역직렬화 시점에 400 으로 거부한다.
 */
public record PaymentWebhookRequest(
        @Schema(description = "PG 거래 식별자", example = "pg-tx-20260101-0001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String pgTransactionId,
        @Schema(description = "결제 결과 — PAID 또는 FAILED 만 허용", example = "PAID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull PaymentStatus status,
        @Schema(description = "실패 사유 — FAILED 일 때만 의미 (선택)", example = "카드 한도 초과")
        String failureReason,
        @Schema(description = "PG 측 처리 시각 (선택)")
        Instant occurredAt) {

    public PaymentWebhookRequest {
        if (status != null && status != PaymentStatus.PAID && status != PaymentStatus.FAILED) {
            throw new IllegalArgumentException("webhook status 는 PAID 또는 FAILED 여야 합니다 (현재: " + status + ")");
        }
    }
}
