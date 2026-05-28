package com.groove.admin.api.dto;

import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponStatus;

import java.time.Instant;

/**
 * 관리자 직접지급 응답 (API.md §3.10 POST /admin/coupons/{id}/grant).
 *
 * <p>발급된 {@code member_coupon} 의 식별자와 만료 시각을 노출해, 호출자가 후속 추적(취소·문의
 * 응답·UI 갱신) 시 추가 조회 없이 결과를 사용할 수 있게 한다. 정책 요약은 별도 GET 으로 충분.
 */
public record AdminMemberCouponResponse(
        Long memberCouponId,
        Long couponId,
        Long memberId,
        MemberCouponStatus status,
        Instant issuedAt,
        Instant expiresAt
) {

    public static AdminMemberCouponResponse from(MemberCoupon memberCoupon) {
        return new AdminMemberCouponResponse(
                memberCoupon.getId(),
                memberCoupon.getCoupon().getId(),
                memberCoupon.getMemberId(),
                memberCoupon.getStatus(),
                memberCoupon.getIssuedAt(),
                memberCoupon.getExpiresAt());
    }
}
