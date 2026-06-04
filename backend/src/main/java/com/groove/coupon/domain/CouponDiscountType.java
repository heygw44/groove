package com.groove.coupon.domain;

/**
 * 쿠폰 할인 방식 (glossary §3.10, docs/plans/coupon-system.md §3.2).
 *
 * <p>할인액 산정을 각 상수의 행위로 위임한다 ({@link com.groove.order.domain.OrderStatus}
 * 의 행위 위임과 동일 스타일). {@link Coupon#calculateDiscount(long)} 가 최소 주문금액 가드와
 * {@code discount ≤ subtotal} 불변식을 적용하기 전, 본 메서드는 "원시 할인액(raw)" 만 계산한다.
 */
public enum CouponDiscountType {

    /** 정액 할인: {@code min(discountValue, subtotal)} — 소계보다 큰 정액은 소계로 캡된다. */
    FIXED_AMOUNT {
        @Override
        long rawDiscount(long subtotal, long discountValue, Long maxDiscountAmount) {
            return Math.min(discountValue, subtotal);
        }
    },

    /**
     * 정률 할인: {@code subtotal * discountValue / 100}, 상한({@code maxDiscountAmount}) 이
     * 있으면 그 값으로 캡된다. {@code discountValue} 는 1~100 범위(정책 생성 시 검증).
     */
    PERCENTAGE {
        @Override
        long rawDiscount(long subtotal, long discountValue, Long maxDiscountAmount) {
            // discountValue 는 1~100 이라 subtotal*discountValue 는 현실적 금액(원, BIGINT)
            // 범위에서 long 오버플로가 발생하지 않는다.
            long raw = subtotal * discountValue / 100;
            return (maxDiscountAmount != null) ? Math.min(raw, maxDiscountAmount) : raw;
        }
    };

    /**
     * 캡·가드 적용 전 원시 할인액. 호출은 {@link Coupon#calculateDiscount(long)} 만 한다.
     */
    abstract long rawDiscount(long subtotal, long discountValue, Long maxDiscountAmount);
}
