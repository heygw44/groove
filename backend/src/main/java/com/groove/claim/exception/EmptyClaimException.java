package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 반품할 항목을 하나도 지정하지 않고 반품을 요청한 경우. HTTP 422.
 */
public class EmptyClaimException extends DomainException {

    public EmptyClaimException() {
        super(ErrorCode.CLAIM_NO_ITEMS);
    }
}
