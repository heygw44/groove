package com.groove.coupon.domain;

/**
 * 쿠폰 할인 방식.
 *
 * <p>할인액 산정을 각 상수의 행위로 위임한다. Coupon.calculateDiscount(long) 가 가드·불변식을 적용하기 전,
 * 본 메서드는 "원시 할인액(raw)" 만 계산한다.
 */
public enum CouponDiscountType {

    /** 정액 할인: min(discountValue, subtotal) — 소계보다 큰 정액은 소계로 캡된다. */
    FIXED_AMOUNT {
        @Override
        long rawDiscount(long subtotal, long discountValue, Long maxDiscountAmount) {
            return Math.min(discountValue, subtotal);
        }
    },

    /**
     * 정률 할인: subtotal * discountValue / 100, 상한(maxDiscountAmount) 이 있으면 그 값으로 캡된다.
     * discountValue 는 1~100 범위.
     */
    PERCENTAGE {
        @Override
        long rawDiscount(long subtotal, long discountValue, Long maxDiscountAmount) {
            long raw = subtotal * discountValue / 100;
            return (maxDiscountAmount != null) ? Math.min(raw, maxDiscountAmount) : raw;
        }
    };

    /** 캡·가드 적용 전 원시 할인액. */
    abstract long rawDiscount(long subtotal, long discountValue, Long maxDiscountAmount);
}
