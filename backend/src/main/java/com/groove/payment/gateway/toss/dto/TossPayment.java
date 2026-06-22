package com.groove.payment.gateway.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 Payment 응답 객체. confirm/query/cancel 응답이 공통으로 이 형태이며, 어댑터는 식별자와 상태만 사용한다.
 *
 * <p>paymentKey: 결제 식별자. status: READY/IN_PROGRESS/WAITING_FOR_DEPOSIT/DONE/CANCELED/PARTIAL_CANCELED/ABORTED/EXPIRED.
 * 그 외 필드(orderId·totalAmount·balanceAmount·cancels 등)는 ignoreUnknown 으로 무시한다 — 실제 소비처가 생기면 추가한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(String paymentKey, String status) {
}
