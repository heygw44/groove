package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/** 이미 사용된 회원 쿠폰(status == USED)의 재적용 시도. HTTP 409. */
public class CouponAlreadyUsedException extends DomainException {

    public CouponAlreadyUsedException(Long memberCouponId) {
        super(ErrorCode.COUPON_ALREADY_USED, "이미 사용한 쿠폰입니다: memberCouponId=" + memberCouponId);
    }
}
