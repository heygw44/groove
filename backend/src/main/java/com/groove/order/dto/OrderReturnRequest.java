package com.groove.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * 반품 요청. 배송완료 후 7일 이내인 상품주문에만 만들 수 있다(D7). 가상계좌 결제면 환불계좌가 필수다(형식은 취소
 * 요청과 같다).
 */
public record OrderReturnRequest(
		@Size(max = 200) String reason,
		@Valid OrderCancelRequest.RefundAccount refundAccount
) {

	public OrderReturnRequest(String reason) {
		this(reason, null);
	}
}
