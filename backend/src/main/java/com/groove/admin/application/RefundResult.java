package com.groove.admin.application;

import com.groove.order.domain.Order;
import com.groove.payment.domain.Payment;

import java.time.Instant;

/**
 * 관리자 환불 처리 결과 — {@link AdminOrderService#refund} 가 반환하고 컨트롤러가 응답 DTO 로 변환한다.
 *
 * @param order           환불 대상 주문 (신규 환불이면 상태 CANCELLED, 멱등 재요청이면 변경 전 상태 그대로)
 * @param payment         결제 (상태 REFUNDED)
 * @param refundedAt      PG 가 통보한 환불 완료 시각 — 멱등 재요청 시에는 {@code null} ({@code Payment} 가 환불 시각 컬럼을 두지 않음)
 * @param alreadyRefunded 이번 요청 이전에 이미 환불돼 부수효과(PG 호출/상태 전이/재고 복원) 없이 응답한 경우 true
 */
public record RefundResult(Order order, Payment payment, Instant refundedAt, boolean alreadyRefunded) {

    public static RefundResult refunded(Order order, Payment payment, Instant refundedAt) {
        return new RefundResult(order, payment, refundedAt, false);
    }

    public static RefundResult alreadyRefunded(Order order, Payment payment) {
        return new RefundResult(order, payment, null, true);
    }
}
