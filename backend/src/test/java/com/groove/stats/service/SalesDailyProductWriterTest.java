package com.groove.stats.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.groove.stats.dto.ProductSalesAggregateRow;

@ExtendWith(MockitoExtension.class)
class SalesDailyProductWriterTest {

	private static final LocalDate SALE_DATE = LocalDate.of(2031, 3, 15);
	private static final LocalDateTime AGGREGATED_AT = LocalDateTime.of(2031, 3, 16, 3, 30);

	@Mock
	private JdbcTemplate jdbcTemplate;

	@InjectMocks
	private SalesDailyProductWriter writer;

	@Nested
	@DisplayName("upsertAll()")
	class UpsertAll {

		@Test
		@DisplayName("행이 비어 있으면 DB 를 건드리지 않는다")
		void doesNotTouchDbWhenRowsEmpty() {
			// when
			writer.upsertAll(SALE_DATE, List.of(), AGGREGATED_AT);

			// then
			verifyNoInteractions(jdbcTemplate);
		}

		@Test
		@DisplayName("행이 있으면 batchUpdate 로 UPSERT 한다")
		void batchUpdatesWhenRowsPresent() {
			// given
			List<ProductSalesAggregateRow> rows = List.of(new ProductSalesAggregateRow(1L, 2L, BigDecimal.TEN, 1L));

			// when
			writer.upsertAll(SALE_DATE, rows, AGGREGATED_AT);

			// then
			verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
		}
	}
}
