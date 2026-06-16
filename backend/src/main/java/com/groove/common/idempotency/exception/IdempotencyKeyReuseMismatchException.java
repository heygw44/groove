package com.groove.common.idempotency.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 이미 처리된 Idempotency-Key 가 다른 요청 페이로드로 재사용된 경우. HTTP 409. */
public class IdempotencyKeyReuseMismatchException extends DomainException {

    public IdempotencyKeyReuseMismatchException(String idempotencyKey) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSE_MISMATCH, "다른 요청에 이미 사용된 Idempotency-Key 입니다: " + idempotencyKey);
    }
}
