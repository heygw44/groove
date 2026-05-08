package com.groove.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/v1/orders 요청 본문 (API.md §3.5).
 *
 * <p>{@code guest} 는 헤더에 Bearer 토큰이 없을 때 필수, 있을 때는 무시된다 —
 * 회원/게스트 분기는 컨트롤러에서 {@code AuthPrincipal} 존재 여부로 판단하고,
 * 본 DTO 는 단일 표현으로 두 경로를 모두 받는다.
 *
 * <p>items 상한 50 — 한 주문에 50개 라인 이상은 비현실적이며 응답 페이로드 비대화 방지.
 */
public record OrderCreateRequest(
        @NotEmpty
        @Size(max = 50)
        @Valid
        List<OrderItemRequest> items,

        @Valid
        GuestInfoRequest guest
) {
}
