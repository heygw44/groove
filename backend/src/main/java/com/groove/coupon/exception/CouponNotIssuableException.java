package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 발급 가능 상태가 아닌 쿠폰 발급 시도 (API.md §3.9). HTTP 422.
 *
 * <p>정책 상태가 {@code ACTIVE} 가 아니거나(SUSPENDED/ENDED) 현재 시각이 발급 기간
 * ({@code valid_from ≤ now ≤ valid_until}) 밖일 때 던진다 — 소진(409)과 구분되는,
 * 정책 자체가 발급 불가인 경우다.
 */
public class CouponNotIssuableException extends DomainException {

    public CouponNotIssuableException(Long couponId) {
        super(ErrorCode.COUPON_NOT_ISSUABLE, "현재 발급할 수 없는 쿠폰입니다: couponId=" + couponId);
    }
}
