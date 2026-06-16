package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

import java.time.Instant;

/**
 * 유효기간이 지난 회원 쿠폰의 주문 적용 시도. HTTP 422.
 *
 * <p>MemberCoupon.expiresAt < now 인 경우 발생한다.
 */
public class CouponExpiredException extends DomainException {

    public CouponExpiredException(Long memberCouponId, Instant expiresAt) {
        super(ErrorCode.COUPON_EXPIRED,
                "유효기간이 만료된 쿠폰입니다: memberCouponId=" + memberCouponId + ", expiresAt=" + expiresAt);
    }
}
