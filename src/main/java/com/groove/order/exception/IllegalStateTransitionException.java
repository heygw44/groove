package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.OrderStatus;

/**
 * 주문 상태 전이 위반 (glossary §3.4, ARCHITECTURE.md §8). HTTP 409.
 *
 * <p>{@link OrderStatus#canTransitionTo(OrderStatus)} 가 false 인 전이가 시도된 경우 발생한다.
 * 종착 상태(COMPLETED/CANCELLED/PAYMENT_FAILED) 에서의 모든 변경 시도, 자기 자신으로의 전이,
 * 정의되지 않은 전이를 모두 포괄한다.
 */
public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(OrderStatus from, OrderStatus to) {
        super(ErrorCode.ORDER_INVALID_STATE_TRANSITION,
                "허용되지 않은 주문 상태 전이입니다: " + from + " -> " + to);
    }
}
