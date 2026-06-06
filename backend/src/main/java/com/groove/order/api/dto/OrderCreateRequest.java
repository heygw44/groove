package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/orders 요청 본문 (API.md §3.5).
 *
 * <p>{@code guest} 는 헤더에 Bearer 토큰이 없을 때 필수, 있을 때는 무시된다 —
 * 회원/게스트 분기는 컨트롤러에서 {@code AuthPrincipal} 존재 여부로 판단하고,
 * 본 DTO 는 단일 표현으로 두 경로를 모두 받는다.
 *
 * <p>{@code shipping} 은 회원/게스트 공통 필수 — 결제 완료 후 이 스냅샷으로 배송 행이 만들어진다(#W7-6).
 *
 * <p>items 상한 50 — 한 주문에 50개 라인 이상은 비현실적이며 응답 페이로드 비대화 방지.
 *
 * <p>{@code memberCouponId} (#91) — optional. 회원 주문에서 쿠폰 1장을 적용한다. 게스트 주문에 동봉되면
 * 서비스 레벨에서 {@code COUPON_NOT_APPLICABLE} (422) 로 거부된다. 검증 어노테이션은 두지 않는다 —
 * null 도 합법이라 빈 값/0 모두 허용한다 (실패는 적용 단계에서 매핑).
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
