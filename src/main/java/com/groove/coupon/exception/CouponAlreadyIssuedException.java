package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 회원당 1장 제약 위반 — 같은 쿠폰을 이미 발급받음 (API.md §3.9). HTTP 409.
 *
 * <p>{@link com.groove.coupon.application.CouponIssueService} 가 사전 검사
 * ({@code existsByCoupon_IdAndMemberId}) 또는 {@code uk_member_coupon_coupon_member} UNIQUE
 * 경합(동시 중복 요청)으로 발급을 거부할 때 던진다 — 두 경로 모두 같은 의미 코드로 응답한다.
 */
public class CouponAlreadyIssuedException extends DomainException {

    public CouponAlreadyIssuedException(Long couponId, Long memberId) {
        super(ErrorCode.COUPON_ALREADY_ISSUED,
                "이미 발급받은 쿠폰입니다: couponId=" + couponId + ", memberId=" + memberId);
    }
}
