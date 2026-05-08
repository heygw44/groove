package com.groove.cart.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 앨범이 SELLING 이 아닌 상태(HIDDEN/SOLD_OUT) 에서 장바구니 추가가 시도된 경우. 422 응답.
 */
public class AlbumNotPurchasableException extends DomainException {

    public AlbumNotPurchasableException() {
        super(ErrorCode.ALBUM_NOT_PURCHASABLE);
    }
}
