package com.groove.common.idempotency.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 컨트롤러 핸들러는 {@code Idempotency-Key} 헤더를 요구한다는 표식.
 *
 * <p>{@link IdempotencyKeyInterceptor} 가 {@code preHandle} 에서 헤더 존재·형식을 검증하고(위반 시
 * HTTP 400), 검증된 키를 요청 속성 {@link IdempotencyKeyInterceptor#KEY_ATTRIBUTE} 으로 노출한다.
 * 핸들러는 그 키를 {@link com.groove.common.idempotency.IdempotencyService} 에 넘겨 멱등 실행한다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
