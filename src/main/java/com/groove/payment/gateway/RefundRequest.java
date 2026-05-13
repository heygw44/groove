package com.groove.payment.gateway;

/**
 * PG 환불 요청 파라미터.
 *
 * @param pgTransactionId 환불 대상 거래 식별자 ({@link PaymentResponse#pgTransactionId()})
 * @param amount          환불 금액 (KRW, 양수 — v1 은 전액 환불만)
 * @param reason          환불 사유 (관리자 입력, 선택)
 * @param idempotencyKey  멱등 키 — 같은 키로 재호출 시 PG 는 첫 응답을 재사용해야 한다 (#72). 호출 측이
 *                        결정적으로 생성해 같은 환불 시도라면 항상 같은 키가 들어오도록 보장한다 (예:
 *                        {@code com.groove.payment.domain.Payment#refundIdempotencyKey()}). blank 불가,
 *                        {@value #MAX_IDEMPOTENCY_KEY_LENGTH}자 이하 (Stripe {@code Idempotency-Key} 한계와 동일).
 */
public record RefundRequest(String pgTransactionId, long amount, String reason, String idempotencyKey) {

    /** PG 헤더 호환을 위한 멱등 키 최대 길이 (Stripe {@code Idempotency-Key} 와 동일). */
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
