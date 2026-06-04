package com.groove.admin.api.dto;

import com.groove.order.api.dto.OrderItemResponse;
import com.groove.order.api.dto.OrderShippingResponse;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;

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
        String orderNumber,
        OrderStatus status,
        Long memberId,
        String guestEmail,
        long totalAmount,
        List<OrderItemResponse> items,
        OrderShippingResponse shipping,
        Instant paidAt,
        Instant cancelledAt,
        String cancelledReason,
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
