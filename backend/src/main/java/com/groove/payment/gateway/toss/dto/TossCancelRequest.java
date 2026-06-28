package com.groove.payment.gateway.toss.dto;

/**
 * 토스 결제 취소 요청 바디(POST /v1/payments/{paymentKey}/cancel).
 * 어댑터가 부분/전액을 동일 경로로 처리해 항상 금액을 명시 전송하므로 cancelAmount 는 primitive long.
 */
public record TossCancelRequest(String cancelReason, long cancelAmount) {
}
