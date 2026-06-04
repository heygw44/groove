package com.groove.admin.api.dto;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponStatus;

import java.time.Instant;

/**
 * 관리자 쿠폰 정책 응답 (API.md §3.10) — GET 목록 · POST 생성 · PATCH 상태변경 응답 공통.
 *
 * <p>회원 노출용 {@code CouponResponse} 와 달리 운영에 필요한 {@code issuedCount}·{@code status} 를
 * 그대로 포함한다.
 */
public record AdminCouponSummary(
        Long couponId,
        String name,
        CouponDiscountType discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        Integer totalQuantity,
        int issuedCount,
        int perMemberLimit,
        Instant validFrom,
        Instant validUntil,
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
