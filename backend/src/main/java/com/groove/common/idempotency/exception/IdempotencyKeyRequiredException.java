package com.groove.common.idempotency.exception;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;

/**
 * {@code @Idempotent} 핸들러에 {@code Idempotency-Key} 헤더가 없거나 형식이 잘못된 경우. HTTP 400.
 */
public class IdempotencyKeyRequiredException extends ValidationException {

    public IdempotencyKeyRequiredException(String detail) {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, detail);
    }
}
