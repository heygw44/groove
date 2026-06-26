package com.groove.claim.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 반품 거부 요청 (POST /admin/claims/{id}/reject). 사유는 선택. */
public record ClaimRejectRequest(
        @Schema(description = "거부 사유 (선택, 500자 이하)", example = "사용 흔적이 있어 반품 불가")
        @Size(max = 500) String reason
) {
}
