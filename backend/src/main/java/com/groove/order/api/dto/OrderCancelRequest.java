package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 주문 취소 요청. reason 은 선택(null/공백 허용), 길이만 제한한다(최대 500).
 */
public record OrderCancelRequest(
        @Schema(description = "취소 사유 (선택, 최대 500자)", example = "단순 변심")
        @Size(max = 500) String reason
) {
}
