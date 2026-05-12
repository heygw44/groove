/**
 * 결제 게이트웨이 추상화 (W7-1, ARCHITECTURE.md §7).
 *
 * <p>{@link com.groove.payment.gateway.PaymentGateway} 인터페이스(Strategy 패턴)와 계약 DTO,
 * 웹훅 발사 포트({@link com.groove.payment.gateway.WebhookDispatcher})를 정의한다. Mock 구현체는
 * {@code mock} 하위 패키지에 두며 {@code @Profile} 로 격리된다 — 실 PG 도입 시 {@code prod}
 * 프로파일 구현체만 추가하면 된다.
 */
package com.groove.payment.gateway;
