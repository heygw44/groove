package com.groove.payment.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 웹훅(PAYMENT_STATUS_CHANGED) 본문. 서명이 없는 요청이라 data 의 status 는 저장(멱등 키)에만 쓰고
 * 상태 판단에는 쓰지 않는다 — 판단은 항상 paymentClient.lookup() 재조회 결과로 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentWebhookRequest(String eventType, OffsetDateTime createdAt, Data data) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(String paymentKey, String orderId, String status) {
	}
}
