package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 본인이 보유하지 않은 회원 쿠폰의 사용 시도. HTTP 403.
 *
 * <p>MemberCoupon.memberId != callerMemberId 인 경우 발생한다.
 */
public class CouponNotOwnedException extends DomainException {

    public CouponNotOwnedException(Long memberCouponId) {
        super(ErrorCode.COUPON_NOT_OWNED, "본인이 보유한 쿠폰이 아닙니다: memberCouponId=" + memberCouponId);
    }
}
