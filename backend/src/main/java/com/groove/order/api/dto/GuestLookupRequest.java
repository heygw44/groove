package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 게스트 주문 조회 요청 — 본문에는 매칭용 email 만 받는다. 불일치 시 404.
 */
public record GuestLookupRequest(
        @Schema(description = "주문 시 입력한 게스트 이메일 (일치해야 조회됨)", example = "guest@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email String email
) {
}
