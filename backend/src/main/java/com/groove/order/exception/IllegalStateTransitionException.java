package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.OrderStatus;

/** 주문 상태 전이 위반 — canTransitionTo 가 false 인 전이가 시도된 경우. HTTP 409. */
public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(OrderStatus from, OrderStatus to) {
        super(ErrorCode.ORDER_INVALID_STATE_TRANSITION,
                "허용되지 않은 주문 상태 전이입니다: " + from + " -> " + to);
    }
}
