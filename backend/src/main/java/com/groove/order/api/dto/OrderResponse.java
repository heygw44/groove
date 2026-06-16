package com.groove.order.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 주문 응답.
 * 금액 3종: totalAmount(상품 합계, 할인 전), discountAmount(쿠폰 할인액),
 * payableAmount(= totalAmount − discountAmount, 실제 청구 금액).
 * trackingNumber 는 배송 생성 시 채워지고 그 전에는 null.
 */
public record OrderResponse(
        @Schema(description = "주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
        String orderNumber,
        @Schema(description = "주문 상태", example = "PAID")
        OrderStatus status,
        @Schema(description = "상품 합계 금액(할인 전, KRW)", example = "45000")
        long totalAmount,
        @Schema(description = "쿠폰 할인액(KRW)", example = "5000")
        long discountAmount,
        @Schema(description = "실제 청구 금액(totalAmount − discountAmount, KRW)", example = "40000")
        long payableAmount,
        @Schema(description = "주문 항목 목록")
        List<OrderItemResponse> items,
        @Schema(description = "주문 시점 캡처된 배송지 스냅샷")
        OrderShippingResponse shipping,
        @Schema(description = "운송장 번호 — 결제 완료 후 배송 생성 시 채워짐, 그 전에는 null", example = "550e8400-e29b-41d4-a716-446655440000",
                nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String trackingNumber,
        @Schema(description = "주문 생성 시각")
        Instant createdAt
) {

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayableAmount(),
                items,
                OrderShippingResponse.from(order.getShippingInfo()),
                order.getTrackingNumber(),
                order.getCreatedAt());
    }
}
