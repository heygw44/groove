package com.groove.admin.api.dto;

import com.groove.order.api.dto.OrderItemResponse;
import com.groove.order.api.dto.OrderShippingResponse;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 주문 상세 / 상태 변경 응답 (이슈 #69).
 *
 * <p>회원용 {@code OrderResponse} 와 달리 소유자 식별 정보({@code memberId}/{@code guestEmail}) 와
 * 결제·취소 추적 시각/사유를 함께 노출한다 — 운영자가 한 화면에서 판단할 수 있도록. 라인/배송지 블록은
 * 회원용 DTO({@link OrderItemResponse}, {@link OrderShippingResponse}) 를 재사용한다.
 */
public record AdminOrderResponse(
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

        @Schema(description = "주문 라인 항목 목록")
        List<OrderItemResponse> items,

        @Schema(description = "배송지 정보")
        OrderShippingResponse shipping,

        @Schema(description = "결제 완료 시각 (미결제면 null, ISO-8601 UTC)", example = "2026-06-06T09:10:00Z")
        Instant paidAt,

        @Schema(description = "취소/환불 시각 (취소되지 않았으면 null)", example = "2026-06-07T11:00:00Z")
        Instant cancelledAt,

        @Schema(description = "취소/환불 사유 (취소되지 않았으면 null)", example = "고객 변심 환불")
        String cancelledReason,

        @Schema(description = "주문 생성 시각 (ISO-8601 UTC)", example = "2026-06-06T09:00:00Z")
        Instant createdAt
) {

    public static AdminOrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new AdminOrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getMemberId(),
                order.getGuestEmail(),
                order.getTotalAmount(),
                items,
                OrderShippingResponse.from(order.getShippingInfo()),
                order.getPaidAt(),
                order.getCancelledAt(),
                order.getCancelledReason(),
                order.getCreatedAt());
    }
}
