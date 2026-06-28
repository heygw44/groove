package com.groove.payment.gateway.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 Payment 응답 객체. confirm/query/cancel 응답 공통 형태이며 어댑터는 식별자·상태·결제수단·정산금액만 사용한다.
 * status: READY/IN_PROGRESS/WAITING_FOR_DEPOSIT/DONE/CANCELED/PARTIAL_CANCELED/ABORTED/EXPIRED.
 * totalAmount 는 권위 결제총액. query 재조회 시 저장 금액과 대조해 위변조를 차단한다. 그 외 필드는 ignoreUnknown.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(String paymentKey, String status, String method, Long totalAmount) {
}
