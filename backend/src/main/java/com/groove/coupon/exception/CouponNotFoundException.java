package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 쿠폰 정책을 찾을 수 없음 — 존재하지 않는 couponId. HTTP 404. */
public class CouponNotFoundException extends DomainException {

    public CouponNotFoundException(Long couponId) {
        super(ErrorCode.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다: couponId=" + couponId);
    }
}
