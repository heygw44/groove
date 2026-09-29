package com.groove.order.dto;

import jakarta.validation.constraints.Size;

/** 반품 요청. 배송완료 후 7일 이내인 상품주문에만 만들 수 있다(D7). */
public record OrderReturnRequest(
		@Size(max = 200) String reason
) {
}
