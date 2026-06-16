package com.groove.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 관리자 환불 요청 (POST /api/v1/admin/orders/{orderNumber}/refund).
 * reason 은 선택이며 지정 시 PG 환불 사유 및 Order.cancelledReason 으로 기록된다. 본문 생략도 허용한다.
 * 길이 상한은 AdminOrderStatusChangeRequest.MAX_REASON_LENGTH.
 */
public record AdminRefundRequest(
        @Schema(description = "환불 사유 — 선택 (PG 환불 사유 및 주문 취소 사유로 기록, 최대 500자)",
                example = "고객 변심 환불")
        @Size(max = AdminOrderStatusChangeRequest.MAX_REASON_LENGTH) String reason) {
}
