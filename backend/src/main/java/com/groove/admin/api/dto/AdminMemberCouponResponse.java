package com.groove.admin.api.dto;

import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 관리자 직접지급 응답 (API.md §3.10 POST /admin/coupons/{id}/grant).
 *
 * <p>발급된 {@code member_coupon} 의 식별자와 만료 시각을 노출해, 호출자가 후속 추적(취소·문의
 * 응답·UI 갱신) 시 추가 조회 없이 결과를 사용할 수 있게 한다. 정책 요약은 별도 GET 으로 충분.
 */
public record AdminMemberCouponResponse(
        @Schema(description = "발급된 회원 쿠폰 ID", example = "501")
        Long memberCouponId,

        @Schema(description = "원본 쿠폰 정책 ID", example = "7")
        Long couponId,

        @Schema(description = "쿠폰을 지급받은 회원 ID", example = "42")
        Long memberId,

        @Schema(description = "회원 쿠폰 상태 — ISSUED(미사용)/USED(사용됨)", example = "ISSUED")
        MemberCouponStatus status,

        @Schema(description = "발급 시각 (ISO-8601 UTC)", example = "2026-06-06T09:00:00Z")
        Instant issuedAt,

        @Schema(description = "만료 시각 (ISO-8601 UTC)", example = "2026-12-31T23:59:59Z")
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
