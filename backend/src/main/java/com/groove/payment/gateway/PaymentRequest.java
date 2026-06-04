package com.groove.payment.gateway;

/**
 * PG 결제 요청 파라미터.
 *
 * <p>게이트웨이 계약 수준의 DTO 다 — REST 요청 본문({@code POST /api/v1/payments}) 과는 별개이며,
 * 결제 API(#W7-3) 가 주문/금액을 매핑해 이 객체로 변환한다.
 *
 * @param orderNumber 외부 노출용 주문 식별자 (예: {@code ORD-20260505-A1B2C3})
 * @param amount      결제 금액 (KRW, 양수)
 */
public record PaymentRequest(String orderNumber, long amount) {

    public PaymentRequest {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber 는 비어 있을 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다 (현재: " + amount + ")");
        }
    }
}
