package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

import java.time.Instant;

/**
 * 유효기간이 지난 회원 쿠폰의 주문 적용 시도 (API.md §3.9). HTTP 422.
 *
 * <p>{@code MemberCoupon.expiresAt < now} 인 경우 발생한다. ISSUED 상태로 남아 있을 수 있으나
 * (스케줄러 만료 배치 도입은 #92~) 적용 시점 검증으로 사용자에게 즉시 거부된다.
 */
public class CouponExpiredException extends DomainException {

    public CouponExpiredException(Long memberCouponId, Instant expiresAt) {
        super(ErrorCode.COUPON_EXPIRED,
                "유효기간이 만료된 쿠폰입니다: memberCouponId=" + memberCouponId + ", expiresAt=" + expiresAt);
    }
}
