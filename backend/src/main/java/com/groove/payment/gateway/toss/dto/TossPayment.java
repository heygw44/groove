package com.groove.payment.gateway.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 Payment 응답 객체. confirm/query/cancel 응답이 공통으로 이 형태이며, 어댑터는 식별자·상태·결제수단·정산금액만 사용한다.
 *
 * <p>paymentKey: 결제 식별자. status: READY/IN_PROGRESS/WAITING_FOR_DEPOSIT/DONE/CANCELED/PARTIAL_CANCELED/ABORTED/EXPIRED.
 * method: 실제 결제수단(한글 "카드"/"가상계좌"/"간편결제"/"휴대폰"/"계좌이체"/상품권). confirm 시 결제수단 정합성 보정에 쓴다.
 * totalAmount: 토스가 알려준 권위 결제총액. query 재조회 시 저장 PENDING 금액과 대조해 위변조를 차단한다(#320).
 * 그 외 필드(orderId·balanceAmount·cancels 등)는 ignoreUnknown 으로 무시한다 — 실제 소비처가 생기면 추가한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(String paymentKey, String status, String method, Long totalAmount) {
}
