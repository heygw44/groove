package com.groove.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.entity.MemberCoupon;

public record AvailableCouponResponse(
		Long memberCouponId,
		String couponCode,
		String couponName,
		DiscountType discountType,
		BigDecimal discountValue,
		BigDecimal minOrderAmount,
		BigDecimal maxDiscountAmount,
		LocalDateTime expiresAt,
		BigDecimal expectedDiscount
) {

	public static AvailableCouponResponse of(MemberCoupon memberCoupon, BigDecimal expectedDiscount) {
		return new AvailableCouponResponse(
				memberCoupon.getId(),
				memberCoupon.getCoupon().getCode(),
				memberCoupon.getCoupon().getName(),
				memberCoupon.getCoupon().getDiscountType(),
				memberCoupon.getCoupon().getDiscountValue(),
				memberCoupon.getCoupon().getMinOrderAmount(),
				memberCoupon.getCoupon().getMaxDiscountAmount(),
				memberCoupon.getCoupon().getExpiresAt(),
				expectedDiscount);
	}
}
