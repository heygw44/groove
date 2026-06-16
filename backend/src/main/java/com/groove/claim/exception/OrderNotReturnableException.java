package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.OrderStatus;

/** 반품을 접수할 수 없는 주문 상태에 반품을 요청한 경우. HTTP 422. */
public class OrderNotReturnableException extends DomainException {

    public OrderNotReturnableException(OrderStatus current) {
        super(ErrorCode.CLAIM_ORDER_NOT_RETURNABLE, "반품할 수 없는 주문 상태입니다: " + current);
    }
}
