package com.groove.payment.gateway;

/**
 * PG 환불 요청 파라미터.
 *
 * @param pgTransactionId 환불 대상 거래 식별자 ({@link PaymentResponse#pgTransactionId()})
 * @param amount          환불 금액 (KRW, 양수 — v1 은 전액 환불만)
 * @param reason          환불 사유 (관리자 입력, 선택)
 */
public record RefundRequest(String pgTransactionId, long amount, String reason) {

    public RefundRequest {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다 (현재: " + amount + ")");
        }
    }
}
