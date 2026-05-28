package com.groove.admin.api.dto;

import com.groove.coupon.domain.CouponStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 쿠폰 상태 변경 요청 (API.md §3.10, PATCH /api/v1/admin/coupons/{id}/status).
 *
 * <p>합법 전이는 {@link CouponStatus#canTransitionTo} 로 서비스가 사전 검증한다 — 위반은 409
 * {@code COUPON_STATUS_TRANSITION_INVALID}. 잘못된 enum 문자열은 바인딩 단계에서 400.
 */
public record AdminCouponStatusChangeRequest(
        @NotNull CouponStatus target
) {
}
