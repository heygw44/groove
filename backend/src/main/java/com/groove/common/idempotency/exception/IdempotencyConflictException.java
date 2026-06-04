package com.groove.common.idempotency.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 동일한 {@code Idempotency-Key} 요청이 아직 처리 중인 경우. HTTP 409.
 *
 * <p>호출자(클라이언트)는 잠시 후 같은 키로 재시도하면 된다 — 선행 처리가 완료되면 캐시된 결과를 받고,
 * 실패했다면 새 처리가 시작된다.
 */
public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String idempotencyKey) {
        super(ErrorCode.IDEMPOTENCY_IN_PROGRESS, "처리 중인 Idempotency-Key 입니다: " + idempotencyKey);
    }
}
