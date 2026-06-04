/**
 * Mock PG 구현 (W7-1, ARCHITECTURE.md §7).
 *
 * <p>실 PG 없이 결제 라이프사이클을 재현한다: {@link com.groove.payment.gateway.mock.MockPaymentGateway}
 * 가 PENDING 응답 + 비동기 결과 결정을, {@link com.groove.payment.gateway.mock.MockWebhookSimulator}
 * 가 지연된 웹훅 콜백 발사를 담당한다. 전체 구성은 {@link com.groove.payment.gateway.mock.MockPaymentConfig}
 * 가 묶으며 모두 {@code @Profile} 로 격리된다.
 */
package com.groove.payment.gateway.mock;
