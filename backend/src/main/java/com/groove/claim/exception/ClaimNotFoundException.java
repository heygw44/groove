package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 반품(claim)을 찾을 수 없는 경우. HTTP 404.
 *
 * <p>존재하지 않는 반품 식별자이거나, 회원이 본인 소유가 아닌 반품을 조회·조작하려 한 경우(소유자 스코프 조회가
 * 빈 결과를 반환)에 던진다 — 후자는 타인 반품의 존재 여부를 노출하지 않으려 404 로 통일한다 (#239).
 */
public class ClaimNotFoundException extends DomainException {

    public ClaimNotFoundException() {
        super(ErrorCode.CLAIM_NOT_FOUND);
    }
}
