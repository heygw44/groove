package com.groove.cart.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 단일 항목 quantity 가 1 미만 또는 Cart.MAX_ITEM_QUANTITY 초과인 경우. 422 응답.
 */
public class CartQuantityLimitExceededException extends DomainException {

    public CartQuantityLimitExceededException() {
        super(ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED);
    }
}
