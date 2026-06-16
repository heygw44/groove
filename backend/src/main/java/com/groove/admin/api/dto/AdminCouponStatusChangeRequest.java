package com.groove.admin.api.dto;

import com.groove.coupon.domain.CouponStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 쿠폰 상태 변경 요청 (PATCH /api/v1/admin/coupons/{id}/status).
 * 합법 전이는 CouponStatus.canTransitionTo 로 서비스가 검증한다(위반은 409). 잘못된 enum 문자열은 400.
 */
public record AdminCouponStatusChangeRequest(
        @Schema(description = "전환할 목표 상태 — ACTIVE/SUSPENDED/ENDED (합법 전이만 허용)", example = "SUSPENDED")
        @NotNull CouponStatus target
) {
}
