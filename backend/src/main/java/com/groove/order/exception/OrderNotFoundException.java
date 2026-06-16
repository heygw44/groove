package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 주문을 찾을 수 없는 경우. HTTP 404. */
public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException() {
        super(ErrorCode.ORDER_NOT_FOUND);
    }
}
