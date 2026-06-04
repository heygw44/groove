package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 쿠폰 정책을 찾을 수 없는 경우 (API.md §3.9). HTTP 404.
 *
 * <p>발급/조회 시 존재하지 않는 {@code couponId} 가 지정된 경우 발생한다.
 */
public class CouponNotFoundException extends DomainException {

    public CouponNotFoundException(Long couponId) {
        super(ErrorCode.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다: couponId=" + couponId);
    }
}
