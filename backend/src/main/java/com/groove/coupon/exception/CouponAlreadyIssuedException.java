package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 회원당 1장 제약 위반 — 같은 쿠폰을 이미 발급받음. HTTP 409.
 */
public class CouponAlreadyIssuedException extends DomainException {

    public CouponAlreadyIssuedException(Long couponId, Long memberId) {
        super(ErrorCode.COUPON_ALREADY_ISSUED,
                "이미 발급받은 쿠폰입니다: couponId=" + couponId + ", memberId=" + memberId);
    }
}
