package com.groove.admin.api.dto;

import com.groove.admin.application.RefundResult;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.domain.PaymentStatus;

import java.time.Instant;

/**
 * 관리자 환불 응답 (이슈 #69).
 *
 * @param orderNumber     주문 번호
 * @param orderStatus     환불 후 주문 상태 — 신규 환불이면 CANCELLED, 멱등 재요청이면 변경 전 상태 그대로
 * @param paymentStatus   결제 상태 — 항상 REFUNDED
 * @param refundedAt      PG 환불 완료 시각 — 멱등 재요청 시 {@code null} ({@code Payment} 가 환불 시각을 영속하지 않음)
 * @param alreadyRefunded 이미 환불된 결제에 재요청해 부수효과 없이 응답한 경우 true
 */
public record AdminRefundResponse(
        String orderNumber,
        OrderStatus orderStatus,
        PaymentStatus paymentStatus,
        Instant refundedAt,
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
