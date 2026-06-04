package com.groove.common.idempotency.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link Idempotent} 핸들러의 {@code Idempotency-Key} 헤더를 검증하는 인터셉터.
 *
 * <p>핸들러가 {@code @Idempotent} 면 {@code preHandle} 에서 헤더를 {@link IdempotencyKeyValidator}
 * 로 검증한다 — 위반 시 {@link com.groove.common.idempotency.exception.IdempotencyKeyRequiredException}
 * (HTTP 400, {@code GlobalExceptionHandler} 가 ProblemDetail 로 변환). 통과하면 검증된 키를 요청 속성
 * {@link #KEY_ATTRIBUTE} 으로 노출해 핸들러가 꺼내 쓰게 한다. {@code @Idempotent} 가 없는 핸들러는 통과.
 */
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";

    /** 검증 통과한 키를 담는 요청 속성 이름. */
    public static final String KEY_ATTRIBUTE = IdempotencyKeyInterceptor.class.getName() + ".key";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod && handlerMethod.hasMethodAnnotation(Idempotent.class)) {
            String key = IdempotencyKeyValidator.validate(request.getHeader(HEADER));
            request.setAttribute(KEY_ATTRIBUTE, key);
        }
        return true;
    }
}
