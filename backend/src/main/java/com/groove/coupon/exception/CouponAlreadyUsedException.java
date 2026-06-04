package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 이미 사용된 회원 쿠폰의 재적용 시도 (API.md §3.9). HTTP 409.
 *
 * <p>{@code MemberCoupon.status == USED} 인 경우 발생한다. 동일 회원이 같은 쿠폰을 두 번 적용하려고
 * 시도하는 경합 (예: 이중 클릭) 에서 행 락 + 상태 검증이 이 예외를 던진다.
 */
public class CouponAlreadyUsedException extends DomainException {

    public CouponAlreadyUsedException(Long memberCouponId) {
        super(ErrorCode.COUPON_ALREADY_USED, "이미 사용한 쿠폰입니다: memberCouponId=" + memberCouponId);
    }
}
