package com.groove.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 관리자 쿠폰 직접지급 요청 (POST /api/v1/admin/coupons/{id}/grant).
 * 선착순 한정수량과 독립적으로 member_coupon 1행을 INSERT 한다(발급 카운터 미증가). 활성 회원만 허용(탈퇴자는 404).
 */
public record AdminCouponGrantRequest(
        @Schema(description = "쿠폰을 지급할 대상 회원 ID", example = "42")
        @NotNull @Positive Long memberId
) {
}
