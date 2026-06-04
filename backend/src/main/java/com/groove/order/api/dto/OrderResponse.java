package com.groove.order.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * 주문 응답 (API.md §3.5).
 *
 * <p>{@code shipping} 은 주문 시점에 캡처된 배송지 스냅샷이다(#W7-6). 라이브 배송 진행 상태는 별개의
 * {@code GET /shippings/{trackingNumber}} 가 다룬다 — 결제 상태(payment) 도 마찬가지로 별도.
 *
 * <p>금액 3종(이슈 #116): {@code totalAmount} 는 상품 합계(할인 전), {@code discountAmount} 는 쿠폰 할인액,
 * {@code payableAmount}({@code = totalAmount − discountAmount}) 는 실제 청구 금액이다 — 쿠폰 적용 주문에서
 * 화면이 올바른 결제 금액을 보이도록 노출한다.
 *
 * <p>{@code trackingNumber}(이슈 #116) 는 결제 완료 후 배송 생성 시 채워진다 — 그 전에는 {@code null}.
 * 이 번호로 프론트가 {@code GET /shippings/{trackingNumber}} 배송 추적을 연결한다.
 */
public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        long totalAmount,
        long discountAmount,
        long payableAmount,
        List<OrderItemResponse> items,
        OrderShippingResponse shipping,
        String trackingNumber,
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
