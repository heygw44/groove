package com.groove.stats.dto;

import java.math.BigDecimal;

/** 하루치 상품별 원본 재계산 결과. 사전 집계 테이블 {@code sales_daily_product} 적재에 쓰는 내부 집계용 DTO. */
public record ProductSalesAggregateRow(
		Long productId,
		long soldQuantity,
		BigDecimal salesAmount,
		long orderCount
) {
}
