package com.groove.coupon.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 쿠폰 최소 주문금액 미충족 (docs/plans/coupon-system.md §3.2). HTTP 422.
 *
 * <p>{@link com.groove.coupon.domain.Coupon#calculateDiscount(long)} 가 주문 소계
 * (subtotal) 가 쿠폰의 {@code minOrderAmount} 미만일 때 던진다.
 */
public class CouponMinOrderNotMetException extends DomainException {

    public CouponMinOrderNotMetException(long subtotal, long minOrderAmount) {
        super(ErrorCode.COUPON_MIN_ORDER_NOT_MET,
                "최소 주문금액 조건을 충족하지 않습니다: subtotal=" + subtotal + ", minOrder=" + minOrderAmount);
    }
}
