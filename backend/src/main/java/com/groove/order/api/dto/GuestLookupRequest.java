package com.groove.order.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 게스트 주문 조회 요청 (API.md §3.5).
 *
 * <p>orderNumber 는 path 에서 받고 본문에는 매칭용 email 만 받는다 — 이메일이 일치해야
 * 주문이 존재한 것으로 간주하며, 불일치 시 404 (정보 노출 회피).
 */
public record GuestLookupRequest(
        @NotBlank @Email String email
) {
}
