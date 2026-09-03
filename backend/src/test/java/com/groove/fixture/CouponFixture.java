package com.groove.fixture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;

public final class CouponFixture {

	private static final BigDecimal DEFAULT_MIN_ORDER_AMOUNT = BigDecimal.ZERO;
	private static final Integer DEFAULT_TOTAL_QUANTITY = null;
	private static final LocalDateTime DEFAULT_EXPIRES_AT = LocalDateTime.now().plusDays(7);

	private CouponFixture() {
	}

	public static Coupon fixed(String code, BigDecimal discountValue) {
		return create(code, DiscountType.FIXED, discountValue, DEFAULT_MIN_ORDER_AMOUNT, null,
				DEFAULT_TOTAL_QUANTITY, DEFAULT_EXPIRES_AT);
	}

	public static Coupon rate(String code, BigDecimal percent, BigDecimal maxDiscountAmount) {
		return create(code, DiscountType.RATE, percent, DEFAULT_MIN_ORDER_AMOUNT, maxDiscountAmount,
				DEFAULT_TOTAL_QUANTITY, DEFAULT_EXPIRES_AT);
	}

	public static Coupon withMinOrderAmount(String code, DiscountType discountType, BigDecimal discountValue,
			BigDecimal minOrderAmount) {
		return create(code, discountType, discountValue, minOrderAmount, null, DEFAULT_TOTAL_QUANTITY,
				DEFAULT_EXPIRES_AT);
	}

	public static Coupon withTotalQuantity(String code, Integer totalQuantity) {
		return create(code, DiscountType.FIXED, BigDecimal.valueOf(1000), DEFAULT_MIN_ORDER_AMOUNT, null,
				totalQuantity, DEFAULT_EXPIRES_AT);
	}

	public static Coupon create(String code, DiscountType discountType, BigDecimal discountValue,
			BigDecimal minOrderAmount, BigDecimal maxDiscountAmount, Integer totalQuantity,
			LocalDateTime expiresAt) {
		return Coupon.create(code, "테스트 쿠폰", discountType, discountValue, minOrderAmount, maxDiscountAmount,
				totalQuantity, expiresAt);
	}

	public static Coupon withId(Coupon coupon, Long id) {
		ReflectionTestUtils.setField(coupon, "id", id);
		return coupon;
	}

	public static Coupon expired(Coupon coupon) {
		ReflectionTestUtils.setField(coupon, "expiresAt", LocalDateTime.now().minusDays(1));
		return coupon;
	}

	public static Coupon withIssuedCount(Coupon coupon, int issuedCount) {
		ReflectionTestUtils.setField(coupon, "issuedCount", issuedCount);
		return coupon;
	}
}
