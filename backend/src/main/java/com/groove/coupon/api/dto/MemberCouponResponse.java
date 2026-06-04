package com.groove.coupon.api.dto;

import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponStatus;

import java.time.Instant;

/**
 * 회원 보유 쿠폰 응답 (API.md §3.9).
 *
 * <p>발급 201 응답({@code POST /coupons/{id}/issue})과 목록({@code GET /members/me/coupons})이 공용으로 쓰는
 * 필드 합집합이다 — 발급 직후엔 {@code usedAt}/{@code orderNumber} 가 null, 목록에선 상태에 따라 채워진다.
 *
 * <p>{@code orderNumber} 는 쿠폰 사용(USED) 시 연결된 주문 번호다. 쿠폰 사용은 주문 통합(#91)에서 처음
 * 발생하므로 본 이슈(#90) 범위에서는 항상 {@code null} 이며, 주문번호 resolve 배선은 #91 에서 추가한다.
 *
 * <p>JSON 직렬화 가능한 단순 DTO 다 — {@code IdempotencyService} 가 발급 응답을 캐싱·replay 하므로
 * (enum·Instant 만 사용) 왕복 가능해야 한다.
 */
public record MemberCouponResponse(
        Long memberCouponId,
        Long couponId,
        String name,
        CouponDiscountType discountType,
        long discountValue,
        Long maxDiscountAmount,
        long minOrderAmount,
        MemberCouponStatus status,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt,
        String orderNumber
) {

    public static MemberCouponResponse from(MemberCoupon memberCoupon) {
        Coupon coupon = memberCoupon.getCoupon();
        return new MemberCouponResponse(
                memberCoupon.getId(),
                coupon.getId(),
                coupon.getName(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinOrderAmount(),
                memberCoupon.getStatus(),
                memberCoupon.getIssuedAt(),
                memberCoupon.getExpiresAt(),
                memberCoupon.getUsedAt(),
                null
        );
    }
}
