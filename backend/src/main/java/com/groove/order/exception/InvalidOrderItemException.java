package com.groove.order.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * OrderItem 의 quantity / unit_price 값이 도메인 제약을 위반한 경우. HTTP 422.
 *
 * <p>{@code quantity > 0}, {@code unit_price >= 0} 위반 시 도메인 메서드에서 발생한다.
 * DB CHECK 제약과 이중 방어선이며 Bean Validation 으로 막힌 입력이 도메인까지 도달하면
 * 본 예외로 처리한다.
 */
public class InvalidOrderItemException extends DomainException {

    public InvalidOrderItemException(String detail) {
        super(ErrorCode.ORDER_ITEM_INVALID, detail);
    }
}
