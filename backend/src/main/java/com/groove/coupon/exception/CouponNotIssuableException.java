package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 발급 가능 상태가 아닌 쿠폰 발급 시도. HTTP 422.
 * 정책 상태가 ACTIVE 아님(SUSPENDED/ENDED) 또는 발급 기간(valid_from ≤ now ≤ valid_until) 밖.
 */
public class CouponNotIssuableException extends DomainException {

    public CouponNotIssuableException(Long couponId) {
        super(ErrorCode.COUPON_NOT_ISSUABLE, "현재 발급할 수 없는 쿠폰입니다: couponId=" + couponId);
    }
}
