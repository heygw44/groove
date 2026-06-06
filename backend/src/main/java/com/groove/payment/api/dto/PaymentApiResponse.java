package com.groove.payment.api.dto;

import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 결제 응답 (API.md §3.6 — PaymentResponse).
 *
 * <p>{@code IdempotencyService} 가 동일 {@code Idempotency-Key} 재요청 시 이 DTO 를 JSON 으로
 * 직렬화해 캐싱하고 그대로 replay 한다 — 따라서 JSON 왕복 가능한 단순 record 여야 한다.
 *
 * @param paymentId   결제 식별자
 * @param orderNumber 주문 외부 식별자
 * @param amount      결제 금액 (KRW)
 * @param status      결제 상태 — 접수 직후에는 항상 {@link PaymentStatus#PENDING}
 * @param method      결제 수단
 * @param pgProvider  PG 식별자 (예: {@code MOCK})
 * @param paidAt      결제 완료 시각 — PENDING 동안 {@code null} (#W7-4 웹훅에서 채워짐)
 * @param createdAt   결제 접수 시각
 */
public record PaymentApiResponse(
        @Schema(description = "결제 식별자", example = "1")
        Long paymentId,
        @Schema(description = "주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
        String orderNumber,
        @Schema(description = "결제 금액(KRW)", example = "40000")
        long amount,
        @Schema(description = "결제 상태 — 접수 직후에는 항상 PENDING", example = "PENDING")
        PaymentStatus status,
        @Schema(description = "결제 수단", example = "CARD")
        PaymentMethod method,
        @Schema(description = "PG 식별자", example = "MOCK")
        String pgProvider,
        @Schema(description = "결제 완료 시각 — PENDING 동안 null")
        Instant paidAt,
        @Schema(description = "결제 접수 시각")
        Instant createdAt) {

    public static PaymentApiResponse from(Payment payment) {
        return new PaymentApiResponse(
                payment.getId(),
                payment.getOrder().getOrderNumber(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getPgProvider(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
