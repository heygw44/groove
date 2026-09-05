package com.groove.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.entity.MemberCoupon;

public record MemberCouponResponse(
		Long memberCouponId,
		Long couponId,
		String couponCode,
		String couponName,
		DiscountType discountType,
		BigDecimal discountValue,
		BigDecimal minOrderAmount,
		BigDecimal maxDiscountAmount,
		LocalDateTime expiresAt,
		boolean used,
		boolean expired,
		LocalDateTime issuedAt,
		LocalDateTime usedAt
) {

	public static MemberCouponResponse from(MemberCoupon memberCoupon) {
		return new MemberCouponResponse(
				memberCoupon.getId(),
				memberCoupon.getCoupon().getId(),
				memberCoupon.getCoupon().getCode(),
				memberCoupon.getCoupon().getName(),
				memberCoupon.getCoupon().getDiscountType(),
				memberCoupon.getCoupon().getDiscountValue(),
				memberCoupon.getCoupon().getMinOrderAmount(),
				memberCoupon.getCoupon().getMaxDiscountAmount(),
				memberCoupon.getCoupon().getExpiresAt(),
				memberCoupon.isUsed(),
				memberCoupon.isExpired(),
				memberCoupon.getIssuedAt(),
				memberCoupon.getUsedAt());
	}
}
