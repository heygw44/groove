package com.groove.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;

public record AdminCouponSummaryResponse(
		Long id,
		String code,
		String name,
		DiscountType discountType,
		BigDecimal discountValue,
		BigDecimal minOrderAmount,
		BigDecimal maxDiscountAmount,
		Integer totalQuantity,
		int issuedCount,
		long usedCount,
		LocalDateTime expiresAt,
		CouponStatus status,
		LocalDateTime createdAt
) {
}
