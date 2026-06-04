package com.groove.shipping.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 배송 정보를 찾을 수 없는 경우. HTTP 404.
 *
 * <p>{@code GET /shippings/{trackingNumber}} 에서 운송장 번호에 해당하는 배송이 없을 때 던진다.
 */
public class ShippingNotFoundException extends DomainException {

    public ShippingNotFoundException() {
        super(ErrorCode.SHIPPING_NOT_FOUND);
    }
}
