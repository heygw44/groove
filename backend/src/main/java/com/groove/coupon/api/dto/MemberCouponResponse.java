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
 * <p>{@code orderNumber} 는 쿠폰 사용(USED) 시 연결된 주문 번호다. 발급(201) 응답에서는 항상 {@code null} 이고,
 * 내 쿠폰 목록 조회에서는 {@code CouponQueryService} 가 {@code orderId → orderNumber} 를 일괄 resolve 해
 * {@link #from(MemberCoupon, String)} 로 주입한다 (배선: #137, 누락됐던 #91 의 후속).
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

    /** 발급(201) 응답 등 주문번호를 알 수 없는 경로용 — {@code orderNumber} 는 항상 {@code null}. */
    public static MemberCouponResponse from(MemberCoupon memberCoupon) {
        return from(memberCoupon, null);
    }

    /** 목록 조회용 — 호출자가 {@code orderId → orderNumber} 를 resolve 해 주입한다 (USED 가 아니면 {@code null}). */
    public static MemberCouponResponse from(MemberCoupon memberCoupon, String orderNumber) {
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
                orderNumber
        );
    }
}
