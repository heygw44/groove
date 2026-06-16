package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 배송완료 시각을 알 수 없어 반품 기한을 산정할 수 없는 경우. HTTP 422. */
public class ReturnWindowNotDeterminableException extends DomainException {

    public ReturnWindowNotDeterminableException() {
        super(ErrorCode.CLAIM_WINDOW_NOT_DETERMINABLE);
    }
}
