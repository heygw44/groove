package com.groove.claim.exception;

import com.groove.claim.domain.ClaimStatus;
import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 허용되지 않은 반품 상태 전이를 시도한 경우. HTTP 409. */
public class ClaimInvalidStateTransitionException extends DomainException {

    public ClaimInvalidStateTransitionException(ClaimStatus from, ClaimStatus to) {
        super(ErrorCode.CLAIM_INVALID_STATE_TRANSITION, "허용되지 않은 반품 상태 전이입니다: " + from + " -> " + to);
    }
}
