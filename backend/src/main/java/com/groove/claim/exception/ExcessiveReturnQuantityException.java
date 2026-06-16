package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 반품 가능 수량을 초과해 반품을 요청한 경우. HTTP 409. */
public class ExcessiveReturnQuantityException extends DomainException {

    public ExcessiveReturnQuantityException(Long orderItemId, int requested, int returnable) {
        super(ErrorCode.CLAIM_QUANTITY_EXCEEDED,
                "반품 가능 수량을 초과했습니다: orderItemId=" + orderItemId + ", 요청=" + requested + ", 가능=" + returnable);
    }
}
