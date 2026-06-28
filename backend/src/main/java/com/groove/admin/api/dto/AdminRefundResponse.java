package com.groove.admin.api.dto;

import com.groove.admin.application.RefundResult;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AdminRefundResponse(
        @Schema(description = "주문 번호 (ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260606-A1B2C3")
        String orderNumber,

        @Schema(description = "환불 후 주문 상태 — 신규 환불이면 CANCELLED, 멱등 재요청이면 변경 전 상태", example = "CANCELLED")
        OrderStatus orderStatus,

        @Schema(description = "결제 상태 — 항상 REFUNDED", example = "REFUNDED")
        PaymentStatus paymentStatus,

        @Schema(description = "PG 환불 완료 시각 — 멱등 재요청 시 null (ISO-8601 UTC)", example = "2026-06-07T11:00:00Z")
        Instant refundedAt,

        @Schema(description = "이미 환불된 결제에 재요청해 부수효과 없이 응답한 경우 true", example = "false")
        boolean alreadyRefunded
) {

    public static AdminRefundResponse from(RefundResult result) {
        return new AdminRefundResponse(
                result.order().getOrderNumber(),
                result.order().getStatus(),
                result.payment().getStatus(),
                result.refundedAt(),
                result.alreadyRefunded());
    }
}
