package com.groove.admin.api.dto;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 관리자 쿠폰 정책 응답 (API.md §3.10) — GET 목록 · POST 생성 · PATCH 상태변경 응답 공통.
 *
 * <p>회원 노출용 {@code CouponResponse} 와 달리 운영에 필요한 {@code issuedCount}·{@code status} 를
 * 그대로 포함한다.
 */
public record AdminCouponSummary(
        @Schema(description = "쿠폰 정책 ID", example = "7")
        Long couponId,

        @Schema(description = "쿠폰 정책 이름", example = "신규가입 1만원 할인")
        String name,

        @Schema(description = "할인 방식 — FIXED_AMOUNT(정액) 또는 PERCENTAGE(정률)", example = "FIXED_AMOUNT")
        CouponDiscountType discountType,

        @Schema(description = "할인값 — 정액이면 원 단위 금액, 정률이면 퍼센트", example = "10000")
        long discountValue,

        @Schema(description = "최대 할인 한도(원) — 정액 쿠폰은 null 일 수 있음", example = "20000")
        Long maxDiscountAmount,

        @Schema(description = "최소 주문 금액(원)", example = "30000")
        long minOrderAmount,

        @Schema(description = "총 발급 한정수량 — null 이면 무제한", example = "1000")
        Integer totalQuantity,

        @Schema(description = "현재까지 발급된 수량 (직접지급은 미포함)", example = "120")
        int issuedCount,

        @Schema(description = "회원 1인당 발급 제한 수량", example = "1")
        int perMemberLimit,

        @Schema(description = "유효기간 시작 시각 (ISO-8601 UTC)", example = "2026-06-01T00:00:00Z")
        Instant validFrom,

        @Schema(description = "유효기간 종료 시각 (ISO-8601 UTC)", example = "2026-12-31T23:59:59Z")
        Instant validUntil,

        @Schema(description = "쿠폰 정책 상태 — ACTIVE/SUSPENDED/ENDED", example = "ACTIVE")
        CouponStatus status
) {

    public static AdminCouponSummary from(Coupon coupon) {
        return new AdminCouponSummary(
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderAmount(),
                coupon.getTotalQuantity(),
                coupon.getIssuedCount(),
                coupon.getPerMemberLimit(),
                coupon.getValidFrom(),
                coupon.getValidUntil(),
                coupon.getStatus());
    }
}
