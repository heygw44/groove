package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCouponStatus;

/**
 * 쿠폰/회원쿠폰 상태 전이 위반 (docs/plans/coupon-system.md §3.3).
 *
 * <p>{@link CouponStatus#canTransitionTo}/{@link MemberCouponStatus#canTransitionTo} 가
 * false 인 전이가 모델 가드 메서드에서 시도된 경우 발생한다.
 *
 * <ul>
 *   <li>{@link CouponStatus} 측 (관리자 정책 운영) — {@link ErrorCode#COUPON_INVALID_STATE_TRANSITION}
 *       으로 409. 사전검증(Service) 과 도메인 가드(Coupon.changeStatus) 모두 같은 코드로 응답해
 *       클라이언트가 단일 코드만 처리하도록 통일한다 ({@code OrderInvalidStateTransitionException}
 *       와 동일 패턴).</li>
 *   <li>{@link MemberCouponStatus} 측 (사용/복원/만료/취소 백스톱) — 사용자向 거부는 서비스가
 *       {@code COUPON_ALREADY_USED}/{@code COUPON_EXPIRED} 등 의미 코드로 선제 처리하므로, 본
 *       예외는 백스톱이라 일반 도메인 규칙 위반({@link ErrorCode#DOMAIN_RULE_VIOLATION}, 422)에
 *       매핑한다.</li>
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
