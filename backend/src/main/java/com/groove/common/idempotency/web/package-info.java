/**
 * 멱등성 헤더 검증 웹 계층.
 *
 * <p>@Idempotent 를 컨트롤러 핸들러에 붙이면 IdempotencyKeyInterceptor 가 Idempotency-Key 헤더
 * 존재·형식을 검증하고(누락 시 400), 검증된 키를 요청 속성으로 노출한다.
 */
package com.groove.common.idempotency.web;
