package com.groove.coupon.api.dto;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;

import java.time.Instant;

/**
 * 발급 가능한 쿠폰 응답 (API.md §3.9 {@code GET /coupons}).
 *
 * <p>{@code remainingQuantity} 는 {@code total_quantity − issued_count} 이며 무제한 발급이면 {@code null}.
 */
public record CouponResponse(
        Long couponId,
        String name,
        CouponDiscountType discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        Integer remainingQuantity,
        Instant validUntil
) {

    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderAmount(),
                coupon.remainingQuantity(),
                coupon.getValidUntil()
        );
    }
}
