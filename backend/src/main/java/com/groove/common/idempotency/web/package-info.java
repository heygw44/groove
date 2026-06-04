/**
 * 멱등성 헤더 검증 웹 계층.
 *
 * <p>{@link com.groove.common.idempotency.web.Idempotent} 를 컨트롤러 핸들러에 붙이면
 * {@link com.groove.common.idempotency.web.IdempotencyKeyInterceptor} 가 {@code Idempotency-Key}
 * 헤더 존재·형식을 검증하고(누락 시 400), 검증된 키를 요청 속성으로 노출한다. 실제 멱등 실행은
 * 핸들러가 그 키를 {@link com.groove.common.idempotency.IdempotencyService} 에 넘겨 수행한다.
 *
 * <p>본 이슈(#W7-2)에는 {@code @Idempotent} 를 다는 핸들러가 아직 없다 — 결제 API(#W7-3)가 첫 소비처다.
 */
package com.groove.common.idempotency.web;
