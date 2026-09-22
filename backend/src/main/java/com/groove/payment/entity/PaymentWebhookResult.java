package com.groove.payment.entity;

/**
 * 웹훅 이벤트 한 건의 처리 결과. 대상 이벤트가 아니거나 모르는 결제인 요청은 애초에 행을 남기지 않아
 * IGNORED/NOT_FOUND 는 없다.
 */
public enum PaymentWebhookResult {
	APPLIED, ERROR
}
