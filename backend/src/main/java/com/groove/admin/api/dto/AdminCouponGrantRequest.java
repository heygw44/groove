package com.groove.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 관리자 쿠폰 직접지급 요청 (API.md §3.10, POST /api/v1/admin/coupons/{id}/grant).
 *
 * <p>선착순 한정수량({@code total_quantity})과 독립적으로 {@code member_coupon} 1행을 INSERT 한다 —
 * 직접지급은 정책의 발급 카운터를 증가시키지 않는다. 활성 회원만 허용(탈퇴자는 404).
 */
public record AdminCouponGrantRequest(
        @Schema(description = "쿠폰을 지급할 대상 회원 ID", example = "42")
        @NotNull @Positive Long memberId
) {
}
