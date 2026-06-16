package com.groove.admin.api.dto;

import com.groove.coupon.domain.CouponDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 관리자 쿠폰 정책 생성 요청 (POST /api/v1/admin/coupons). totalQuantity 가 null 이면 무제한 발급. */
public record AdminCouponCreateRequest(
        @Schema(description = "쿠폰 정책 이름 (최대 100자)", example = "신규가입 1만원 할인")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "할인 방식 — FIXED_AMOUNT(정액) 또는 PERCENTAGE(정률)", example = "FIXED_AMOUNT")
        @NotNull CouponDiscountType discountType,

        @Schema(description = "할인값 — 정액이면 원 단위 금액, 정률이면 1~100 퍼센트", example = "10000")
        @Positive long discountValue,

        @Schema(description = "최대 할인 한도(원) — 정률 쿠폰은 필수, 정액 쿠폰은 보통 null", example = "20000")
        @Positive Long maxDiscountAmount,

        @Schema(description = "최소 주문 금액(원) — 이 금액 이상일 때만 사용 가능", example = "30000")
        @PositiveOrZero long minOrderAmount,

        @Schema(description = "총 발급 한정수량 — null 이면 무제한(직접지급 전용)", example = "1000")
        @PositiveOrZero Integer totalQuantity,

        @Schema(description = "회원 1인당 발급 제한 수량", example = "1")
        @Positive int perMemberLimit,

        @Schema(description = "유효기간 시작 시각 (ISO-8601 UTC)", example = "2026-06-01T00:00:00Z")
        @NotNull Instant validFrom,

        @Schema(description = "유효기간 종료 시각 (ISO-8601 UTC, validFrom 이후)", example = "2026-12-31T23:59:59Z")
        @NotNull Instant validUntil
) {
}
