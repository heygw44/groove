package com.groove.coupon.api.dto;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 발급 가능한 쿠폰 응답 (GET /coupons).
 * remainingQuantity 는 total_quantity − issued_count 이며 무제한 발급이면 null.
 */
public record CouponResponse(
        @Schema(description = "쿠폰 식별자", example = "1")
        Long couponId,
        @Schema(description = "쿠폰 이름", example = "신규 가입 10% 할인")
        String name,
        @Schema(description = "할인 유형 (정액 FIXED · 정률 PERCENTAGE)", example = "PERCENTAGE")
        CouponDiscountType discountType,
        @Schema(description = "할인 값 (FIXED 는 원 단위 금액, PERCENTAGE 는 할인율 %)", example = "10")
        long discountValue,
        @Schema(description = "최대 할인 금액 (PERCENTAGE 상한, 무제한이면 null)", example = "5000")
        Long maxDiscountAmount,
        @Schema(description = "최소 주문 금액 (원)", example = "30000")
        long minOrderAmount,
        @Schema(description = "남은 발급 수량 (total_quantity − issued_count, 무제한 발급이면 null)", example = "97")
        Integer remainingQuantity,
        @Schema(description = "발급 유효기간 만료 시각 (ISO-8601 UTC)", example = "2026-12-31T23:59:59Z")
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
