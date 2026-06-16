package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/orders 요청 본문.
 * guest 는 Bearer 토큰이 없을 때 필수, shipping 은 회원/게스트 공통 필수, items 상한 50.
 * memberCouponId 는 optional — 회원 주문에서 쿠폰 1장을 적용한다.
 */
public record OrderCreateRequest(
        @Schema(description = "주문 항목 목록 (1~50개)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        @Size(max = 50)
        @Valid
        List<OrderItemRequest> items,

        @Schema(description = "게스트 주문자 정보 — 비로그인 주문 시 필수, 로그인 주문 시 무시됨")
        @Valid
        GuestInfoRequest guest,

        @Schema(description = "배송지 정보 (회원/게스트 공통 필수)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Valid
        ShippingInfoRequest shipping,

        @Schema(description = "적용할 회원 쿠폰 ID (선택, 회원 주문에서만 유효)", example = "10")
        Long memberCouponId
) {
}
