package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentStatus;

/**
 * PG 동기 승인(confirm) 응답.
 * status 는 PENDING/PAID/FAILED 중 하나다(환불 상태는 confirm 결과일 수 없다).
 * method 는 PG 가 알려준 실제 결제수단(정합성 보정용). 미상이면 null 이고 호출부가 보정을 건너뛴다.
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
