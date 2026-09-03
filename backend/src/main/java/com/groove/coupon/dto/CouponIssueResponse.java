package com.groove.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.entity.MemberCoupon;

public record CouponIssueResponse(
		Long memberCouponId,
		String couponCode,
		String couponName,
		DiscountType discountType,
		BigDecimal discountValue,
		LocalDateTime expiresAt
) {

	public static CouponIssueResponse from(MemberCoupon memberCoupon) {
		return new CouponIssueResponse(
				memberCoupon.getId(),
				memberCoupon.getCoupon().getCode(),
				memberCoupon.getCoupon().getName(),
				memberCoupon.getCoupon().getDiscountType(),
				memberCoupon.getCoupon().getDiscountValue(),
				memberCoupon.getCoupon().getExpiresAt());
	}
}
