package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** OrderItem 의 quantity > 0, unit_price >= 0 위반. HTTP 422. */
public class InvalidOrderItemException extends DomainException {

    public InvalidOrderItemException(String detail) {
        super(ErrorCode.ORDER_ITEM_INVALID, detail);
    }
}
