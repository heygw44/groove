package com.groove.payment.gateway;

/**
 * PG 환불 요청 파라미터.
 * pgTransactionId: 환불 대상 거래. amount: 환불 금액. reason: 사유(선택).
 * idempotencyKey: 멱등 키 — 같은 키 재호출은 첫 응답 재사용. blank 불가, {@value #MAX_IDEMPOTENCY_KEY_LENGTH}자 이하.
 */
public record RefundRequest(String pgTransactionId, long amount, String reason, String idempotencyKey) {

    /** 멱등 키 최대 길이. */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    public RefundRequest {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId 는 비어 있을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다 (현재: " + amount + ")");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 는 비어 있을 수 없습니다");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "idempotencyKey 길이는 " + MAX_IDEMPOTENCY_KEY_LENGTH + "자 이하여야 합니다 (현재: " + idempotencyKey.length() + ")");
        }
    }
}
