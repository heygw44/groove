package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 쿠폰 최소 주문금액 미충족 — subtotal < minOrderAmount. HTTP 422. */
public class CouponMinOrderNotMetException extends DomainException {

    public CouponMinOrderNotMetException(long subtotal, long minOrderAmount) {
        super(ErrorCode.COUPON_MIN_ORDER_NOT_MET,
                "최소 주문금액 조건을 충족하지 않습니다: subtotal=" + subtotal + ", minOrder=" + minOrderAmount);
    }
}
