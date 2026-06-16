package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 주문 생성 요청의 단일 라인. quantity 상한 99.
 */
public record OrderItemRequest(
        @Schema(description = "주문할 앨범 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        Long albumId,

        @Schema(description = "수량 (1~99)", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(1)
        @Max(99)
        int quantity
) {
}
