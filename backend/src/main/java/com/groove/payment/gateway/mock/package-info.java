/**
 * Mock PG 구현.
 *
 * <p>MockPaymentGateway 가 PENDING 응답과 비동기 결과 결정을, MockWebhookSimulator 가 지연된 웹훅 콜백 발사를
 * 담당하며, MockPaymentConfig 가 전체 구성을 묶는다.
 */
package com.groove.payment.gateway.mock;
