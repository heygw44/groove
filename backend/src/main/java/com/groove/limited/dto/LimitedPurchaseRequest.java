package com.groove.limited.dto;

import jakarta.validation.constraints.NotNull;

/** 한정반 구매 요청. */
public record LimitedPurchaseRequest(
		@NotNull(message = "배송지 ID는 필수입니다.")
		Long addressId
) {
}
