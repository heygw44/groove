package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 반품(claim)을 찾을 수 없는 경우. HTTP 404. */
public class ClaimNotFoundException extends DomainException {

    public ClaimNotFoundException() {
        super(ErrorCode.CLAIM_NOT_FOUND);
    }
}
