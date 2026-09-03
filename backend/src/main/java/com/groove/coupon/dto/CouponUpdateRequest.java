package com.groove.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 부분 수정 요청. 필드가 null 이면 기존 값을 유지한다. */
public record CouponUpdateRequest(
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String name,

		DiscountType discountType,

		@DecimalMin(value = "0", inclusive = false, message = "할인 값은 0보다 커야 합니다.")
		@Digits(integer = 8, fraction = 2, message = "할인 값은 정수부 8자리, 소수부 2자리 이하여야 합니다.")
		BigDecimal discountValue,

		@DecimalMin(value = "0", message = "최소 주문 금액은 0 이상이어야 합니다.")
		@Digits(integer = 8, fraction = 2, message = "최소 주문 금액은 정수부 8자리, 소수부 2자리 이하여야 합니다.")
		BigDecimal minOrderAmount,

		@DecimalMin(value = "0", inclusive = false, message = "최대 할인 한도는 0보다 커야 합니다.")
		@Digits(integer = 8, fraction = 2, message = "최대 할인 한도는 정수부 8자리, 소수부 2자리 이하여야 합니다.")
		BigDecimal maxDiscountAmount,

		@Positive(message = "총 수량은 1 이상이어야 합니다.")
		Integer totalQuantity,

		@Future(message = "만료일은 현재 시각 이후여야 합니다.")
		LocalDateTime expiresAt,

		CouponStatus status
) {

	@AssertTrue(message = "정률 할인은 1~100 사이여야 합니다.")
	public boolean isRateWithinRange() {
		if (discountType != DiscountType.RATE || discountValue == null) {
			return true;
		}
		return discountValue.compareTo(BigDecimal.ONE) >= 0 && discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
	}
}
