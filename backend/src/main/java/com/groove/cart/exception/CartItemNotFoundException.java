package com.groove.cart.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 장바구니 항목 ID 가 존재하지 않거나 본인 cart 에 속하지 않는 경우. 404 응답.
 * 본인 외 cart 의 itemId 를 조회/삭제 시도해도 동일하게 404 로 응답한다.
 */
public class CartItemNotFoundException extends DomainException {

    public CartItemNotFoundException() {
        super(ErrorCode.CART_ITEM_NOT_FOUND);
    }
}
