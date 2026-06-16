package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 반품 요청 항목이 대상 주문에 포함돼 있지 않은 경우. HTTP 422. */
public class ClaimItemNotInOrderException extends DomainException {

    public ClaimItemNotInOrderException(Long orderItemId) {
        super(ErrorCode.CLAIM_ITEM_NOT_IN_ORDER, "주문에 포함되지 않은 항목입니다: orderItemId=" + orderItemId);
    }
}
