package com.groove.order.dto;

import jakarta.validation.constraints.Size;

/** 관리자 판매취소(상품 단위). 요청 기록 → 즉시 부분환불로 처리한다. */
public record AdminOrderItemCancelRequest(
		@Size(max = 200) String reason
) {
}
