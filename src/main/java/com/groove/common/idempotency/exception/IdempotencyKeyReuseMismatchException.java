package com.groove.common.idempotency.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 이미 처리된 {@code Idempotency-Key} 가 다른 요청 페이로드로 재사용된 경우. HTTP 409.
 *
 * <p>키는 (요청, 결과) 한 쌍에 1:1 로 묶인다 — 같은 키로 다른 요청을 보내면 캐시된 결과를 돌려주는 것이
 * 명백히 잘못이므로 충돌로 거부한다. 호출자는 새 요청에 새 키를 부여해야 한다.
 */
public class IdempotencyKeyReuseMismatchException extends DomainException {

    public IdempotencyKeyReuseMismatchException(String idempotencyKey) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSE_MISMATCH, "다른 요청에 이미 사용된 Idempotency-Key 입니다: " + idempotencyKey);
    }
}
