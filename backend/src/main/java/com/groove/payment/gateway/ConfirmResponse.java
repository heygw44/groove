package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentStatus;

/**
 * PG 동기 승인(confirm)에 대한 응답.
 *
 * <p>pgTransactionId: 승인된 거래 식별자(토스의 paymentKey). status: 승인 후 확정 상태.
 * confirm 은 동기 확정이므로 status 는 PENDING/PAID/FAILED 중 하나이며, 환불 상태(REFUNDED/PARTIALLY_REFUNDED)는
 * confirm 의 결과일 수 없다.
 * method: PG 가 알려준 실제 결제수단. 결제수단 정합성 보정에 쓰며, 미상이면 {@code null}(Mock·미지 수단). null 이면
 * 호출부가 보정을 건너뛴다.
 */
public record ConfirmResponse(String pgTransactionId, PaymentStatus status, PaymentMethod method) {

    public ConfirmResponse {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 는 null 일 수 없습니다");
        }
        if (status == PaymentStatus.REFUNDED || status == PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalArgumentException("confirm 응답 status 는 환불 상태일 수 없습니다 (현재: " + status + ")");
        }
    }
}
