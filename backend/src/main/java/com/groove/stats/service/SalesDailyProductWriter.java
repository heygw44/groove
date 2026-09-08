package com.groove.stats.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.groove.stats.dto.ProductSalesAggregateRow;

import lombok.RequiredArgsConstructor;

/**
 * sales_daily_product 다건 UPSERT. 리포지토리 네이티브 쿼리를 행마다 호출하면 상품 수만큼 라운드트립이 생겨,
 * {@link JdbcTemplate#batchUpdate} 로 한 청크를 한 번에 보낸다. 가산이 아니라 항상 VALUES(...) 로 덮어쓴다.
 */
@Component
@RequiredArgsConstructor
public class SalesDailyProductWriter {

	private static final String UPSERT_SQL = """
			INSERT INTO sales_daily_product
				(sale_date, product_id, sold_quantity, sales_amount, order_count, aggregated_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
				sold_quantity = VALUES(sold_quantity),
				sales_amount = VALUES(sales_amount),
				order_count = VALUES(order_count),
				aggregated_at = VALUES(aggregated_at),
				updated_at = VALUES(updated_at)
			""";

	private final JdbcTemplate jdbcTemplate;

	public void upsertAll(LocalDate saleDate, List<ProductSalesAggregateRow> rows, LocalDateTime aggregatedAt) {
		if (rows.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(UPSERT_SQL, rows, rows.size(), (ps, row) -> {
			ps.setObject(1, saleDate);
			ps.setLong(2, row.productId());
			ps.setLong(3, row.soldQuantity());
			ps.setBigDecimal(4, row.salesAmount());
			ps.setLong(5, row.orderCount());
			ps.setObject(6, aggregatedAt);
			ps.setObject(7, aggregatedAt);
			ps.setObject(8, aggregatedAt);
		});
	}
}
