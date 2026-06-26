package com.groove.common.idempotency.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 핸들러가 Idempotency-Key 헤더를 요구한다는 표식. IdempotencyKeyInterceptor 가 preHandle 에서
 * 헤더 존재·형식을 검증하고(위반 시 HTTP 400) 검증된 키를 요청 속성 KEY_ATTRIBUTE 으로 노출한다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
