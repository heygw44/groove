package com.groove.payment.gateway.toss.dto;

/**
 * 토스 결제 취소 요청 바디 — {@code POST /v1/payments/{paymentKey}/cancel}.
 *
 * <p>cancelReason: 취소 사유(토스 필수, 최대 200자). cancelAmount: 취소 금액(원).
 * 어댑터는 부분/전액을 동일 경로로 처리해 항상 금액을 명시 전송하므로 primitive long 으로 둔다.
 */
public record TossCancelRequest(String cancelReason, long cancelAmount) {
}
