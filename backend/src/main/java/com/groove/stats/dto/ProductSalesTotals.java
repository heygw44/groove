package com.groove.stats.dto;

import java.math.BigDecimal;

/** sales_daily_product 의 특정 날짜 합계. 대사는 상품 단위 전수 비교 대신 이 합계만 원본과 비교한다. */
public record ProductSalesTotals(
		long soldQuantity,
		BigDecimal salesAmount,
		long orderCount
) {
}
