package com.groove.admin.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 관리자 주문 목록(요약) 응답. 소유자 식별 정보(memberId/guestEmail)를 노출한다.
 * 라인 단위 정보는 상세(AdminOrderResponse)에서.
 */
public record AdminOrderSummaryResponse(
        @Schema(description = "주문 번호 (ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260606-A1B2C3")
        String orderNumber,

        @Schema(description = "주문 상태", example = "PAID")
        OrderStatus status,

        @Schema(description = "회원 주문이면 회원 ID, 게스트 주문이면 null", example = "42")
        Long memberId,

        @Schema(description = "게스트 주문이면 주문자 이메일, 회원 주문이면 null", example = "guest@example.com")
        String guestEmail,

        @Schema(description = "주문 총액(원)", example = "45000")
        long totalAmount,

        @Schema(description = "주문 라인 항목 개수", example = "3")
        int itemCount,

        @Schema(description = "주문 생성 시각 (ISO-8601 UTC)", example = "2026-06-06T09:00:00Z")
        Instant createdAt
) {

    public static AdminOrderSummaryResponse from(Order order) {
        return new AdminOrderSummaryResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getMemberId(),
                order.getGuestEmail(),
                order.getTotalAmount(),
                order.getItems().size(),
                order.getCreatedAt());
    }
}
