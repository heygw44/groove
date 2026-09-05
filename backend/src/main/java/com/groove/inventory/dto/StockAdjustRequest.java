package com.groove.inventory.dto;

import com.groove.inventory.entity.StockChangeType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockAdjustRequest(
		@NotNull(message = "변경 유형은 필수입니다.")
		StockChangeType changeType,

		@NotNull(message = "수량은 필수입니다.")
		@Positive(message = "수량은 1 이상이어야 합니다.")
		Integer quantity,

		@Size(max = 200, message = "사유는 200자 이하여야 합니다.")
		String reason
) {
}
