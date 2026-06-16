package com.groove.claim.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.OrderStatus;

/** 부분 취소를 할 수 없는 주문 상태에 취소를 요청한 경우. HTTP 422. */
public class OrderNotCancellableException extends DomainException {

    public OrderNotCancellableException(OrderStatus current) {
        super(ErrorCode.CLAIM_ORDER_NOT_CANCELLABLE, "부분 취소할 수 없는 주문 상태입니다: " + current);
    }
}
