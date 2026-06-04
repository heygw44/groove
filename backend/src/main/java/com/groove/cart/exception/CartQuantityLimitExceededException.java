package com.groove.cart.exception;

import com.groove.cart.domain.Cart;
import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 단일 항목 quantity 가 1 미만 또는 {@link Cart#MAX_ITEM_QUANTITY} 초과인 경우. 422 응답.
 *
 * <p>Bean Validation (@Min/@Max) 이 컨트롤러 진입 시점에 1차 방어, 도메인이 2차 방어선이다.
 */
public class CartQuantityLimitExceededException extends DomainException {

    public CartQuantityLimitExceededException() {
        super(ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED);
    }
}
