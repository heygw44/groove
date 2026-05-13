package com.groove.admin.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;

import java.time.Instant;

/**
 * 관리자 주문 목록(요약) 응답 (이슈 #69).
 *
 * <p>회원용 {@code OrderSummaryResponse} 의 "대표 앨범 제목" 대신 운영에 필요한 소유자 식별 정보
 * ({@code memberId}/{@code guestEmail}) 를 노출한다. 라인 단위 정보는 상세({@link AdminOrderResponse}) 에서.
 */
public record AdminOrderSummaryResponse(
        String orderNumber,
        OrderStatus status,
        Long memberId,
        String guestEmail,
        long totalAmount,
        int itemCount,
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
