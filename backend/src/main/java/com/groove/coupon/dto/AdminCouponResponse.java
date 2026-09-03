package com.groove.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;

public record AdminCouponResponse(
		Long id,
		String code,
		String name,
		DiscountType discountType,
		BigDecimal discountValue,
		BigDecimal minOrderAmount,
		BigDecimal maxDiscountAmount,
		Integer totalQuantity,
		int issuedCount,
		LocalDateTime expiresAt,
		CouponStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static AdminCouponResponse from(Coupon coupon) {
		return new AdminCouponResponse(
				coupon.getId(),
				coupon.getCode(),
				coupon.getName(),
				coupon.getDiscountType(),
				coupon.getDiscountValue(),
				coupon.getMinOrderAmount(),
				coupon.getMaxDiscountAmount(),
				coupon.getTotalQuantity(),
				coupon.getIssuedCount(),
				coupon.getExpiresAt(),
				coupon.getStatus(),
				coupon.getCreatedAt(),
				coupon.getUpdatedAt());
	}
}
