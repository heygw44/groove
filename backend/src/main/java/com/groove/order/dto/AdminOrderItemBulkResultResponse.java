package com.groove.order.dto;

/** 상품주문 일괄 처리 결과. 대상이 아닌 항목은 예외 없이 건너뛰고 건수로만 알려준다. */
public record AdminOrderItemBulkResultResponse(
		int processed,
		int skipped
) {
}
