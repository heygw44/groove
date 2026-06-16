package com.groove.common.idempotency.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 동일한 Idempotency-Key 요청이 아직 처리 중인 경우. HTTP 409. */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String idempotencyKey) {
        super(ErrorCode.IDEMPOTENCY_IN_PROGRESS, "처리 중인 Idempotency-Key 입니다: " + idempotencyKey);
    }
}
