package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 주문 소유 형식 위반 — member_id XOR guest_email 규칙 위반(동시 채움 또는 둘 다 빔). HTTP 422. */
public class InvalidOrderOwnershipException extends DomainException {

    public InvalidOrderOwnershipException() {
        super(ErrorCode.ORDER_INVALID_OWNERSHIP);
    }
}
