package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 반품 가능 기한(수령 후 N일)이 지난 주문에 반품을 요청한 경우. HTTP 422. */
public class ReturnWindowExpiredException extends DomainException {

    public ReturnWindowExpiredException() {
        super(ErrorCode.CLAIM_WINDOW_EXPIRED);
    }
}
