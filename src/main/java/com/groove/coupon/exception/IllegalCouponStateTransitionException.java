package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCouponStatus;

/**
 * 쿠폰/회원쿠폰 상태 전이 위반 (docs/plans/coupon-system.md §3.3).
 *
 * <p>{@link CouponStatus#canTransitionTo}/{@link MemberCouponStatus#canTransitionTo} 가
 * false 인 전이가 모델 가드 메서드에서 시도된 경우 발생한다. 사용자向 거부(이미 사용·만료 등)는
 * 서비스 계층이 {@code COUPON_ALREADY_USED}/{@code COUPON_EXPIRED} 등 의미 코드로 선제 처리하고,
 * 본 예외는 그 백스톱이므로 일반 도메인 규칙 위반({@link ErrorCode#DOMAIN_RULE_VIOLATION}, 422)에
 * 매핑한다.
 */
public class IllegalCouponStateTransitionException extends DomainException {

    public IllegalCouponStateTransitionException(CouponStatus from, CouponStatus to) {
        super(ErrorCode.DOMAIN_RULE_VIOLATION,
                "허용되지 않은 쿠폰 상태 전이입니다: " + from + " -> " + to);
    }

    public IllegalCouponStateTransitionException(MemberCouponStatus from, MemberCouponStatus to) {
        super(ErrorCode.DOMAIN_RULE_VIOLATION,
                "허용되지 않은 회원 쿠폰 상태 전이입니다: " + from + " -> " + to);
    }
}
