package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 한정수량이 모두 소진된 쿠폰 발급 시도. HTTP 409.
 *
 * <p>원자적 조건부 UPDATE 가 issued_count < total_quantity 를 만족하지 못해 0행을 반환할 때 던진다.
 */
public class CouponSoldOutException extends DomainException {

    public CouponSoldOutException(Long couponId) {
        super(ErrorCode.COUPON_SOLD_OUT, "쿠폰이 모두 소진되었습니다: couponId=" + couponId);
    }
}
