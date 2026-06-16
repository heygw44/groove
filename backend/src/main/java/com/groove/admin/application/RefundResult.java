package com.groove.admin.application;

import com.groove.order.domain.Order;
import com.groove.payment.domain.Payment;

import java.time.Instant;

/**
 * 관리자 환불 처리 결과 — AdminOrderService.refund 가 반환하고 컨트롤러가 응답 DTO 로 변환한다.
 * refundedAt 은 멱등 재요청 시 null, alreadyRefunded 는 부수효과 없이 응답한 경우 true.
 */
public record RefundResult(Order order, Payment payment, Instant refundedAt, boolean alreadyRefunded) {

    public static RefundResult refunded(Order order, Payment payment, Instant refundedAt) {
        return new RefundResult(order, payment, refundedAt, false);
    }

    public static RefundResult alreadyRefunded(Order order, Payment payment) {
        return new RefundResult(order, payment, null, true);
    }
}
