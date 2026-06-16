package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCouponStatus;

/**
 * 쿠폰/회원쿠폰 상태 전이 위반.
 *
 * <p>CouponStatus.canTransitionTo / MemberCouponStatus.canTransitionTo 가 false 인 전이가 모델 가드
 * 메서드에서 시도된 경우 발생한다.
 *
 * <ul>
 *   <li>CouponStatus 측 — COUPON_INVALID_STATE_TRANSITION 으로 409.</li>
 *   <li>MemberCouponStatus 측 — DOMAIN_RULE_VIOLATION 으로 422.</li>
 * </ul>
 */
public class IllegalCouponStateTransitionException extends DomainException {

    public IllegalCouponStateTransitionException(CouponStatus from, CouponStatus to) {
        super(ErrorCode.COUPON_INVALID_STATE_TRANSITION,
                from + " 상태에서는 " + to + " (으)로 변경할 수 없습니다");
    }

    public IllegalCouponStateTransitionException(MemberCouponStatus from, MemberCouponStatus to) {
        super(ErrorCode.DOMAIN_RULE_VIOLATION,
                "허용되지 않은 회원 쿠폰 상태 전이입니다: " + from + " -> " + to);
    }
}
