package com.groove.admin.api.dto;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 관리자 쿠폰 정책 생성 요청 (API.md §3.10, POST /api/v1/admin/coupons).
 *
 * <p>형식·필수값 가드는 Bean Validation 으로 1차 차단하고, 정률 1~100·{@code validUntil>validFrom}
 * 같은 의미 검증은 {@link Coupon.Builder#build()} 가 단일 진입점으로 처리한다 — 서비스는 도메인이
 * 던진 {@code IllegalArgumentException} 을 {@code VALIDATION_FAILED}(400) 로 매핑한다.
 *
 * <p>{@code totalQuantity} 가 {@code null} 이면 무제한 발급 정책(직접지급 전용 시나리오).
 */
public record AdminCouponCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull CouponDiscountType discountType,
        @Positive long discountValue,
        @Positive Long maxDiscountAmount,
        @PositiveOrZero long minOrderAmount,
        @PositiveOrZero Integer totalQuantity,
        @Positive int perMemberLimit,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil
) {
}
