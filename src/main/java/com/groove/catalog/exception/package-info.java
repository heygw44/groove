/**
 * 카탈로그 도메인 예외.
 *
 * <p>{@code *Duplicated} 는 409, {@code *NotFound} 는 404 로 응답된다.
 * 모두 {@link com.groove.common.exception.DomainException} 을 상속해 {@code GlobalExceptionHandler}
 * 의 ProblemDetail 변환 흐름을 그대로 탄다.
 */
package com.groove.catalog.exception;
