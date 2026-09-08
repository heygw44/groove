package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.stats.dto.DailySalesAggregateRow;
import com.groove.stats.dto.ProductSalesAggregateRow;
import com.groove.stats.mapper.SalesAggregationQueryMapper;
import com.groove.stats.repository.SalesDailyProductRepository;
import com.groove.stats.repository.SalesDailyRepository;

@ExtendWith(MockitoExtension.class)
class SalesAggregationServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDate SALE_DATE = LocalDate.of(2031, 3, 15);

	@Mock
	private SalesAggregationQueryMapper salesAggregationQueryMapper;

	@Mock
	private SalesDailyRepository salesDailyRepository;

	@Mock
	private SalesDailyProductRepository salesDailyProductRepository;

	@Mock
	private SalesDailyProductWriter salesDailyProductWriter;

	private SalesAggregationService service;

	private Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2031-03-16T03:30:00Z"), ZONE);
		service = new SalesAggregationService(salesAggregationQueryMapper, salesDailyRepository,
				salesDailyProductRepository, salesDailyProductWriter, clock);
	}

	@Nested
	@DisplayName("aggregateDate()")
	class AggregateDate {

		@Test
		@DisplayName("결제가 하나도 없어도 0 값 1행으로 sales_daily 를 UPSERT 한다")
		void upsertsSalesDailyEvenWhenNoPayments() {
			// given
			given(salesAggregationQueryMapper.findDailySalesOf(SALE_DATE))
					.willReturn(new DailySalesAggregateRow(0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO));
			given(salesAggregationQueryMapper.findProductSalesOf(SALE_DATE)).willReturn(List.of());

			// when
			service.aggregateDate(SALE_DATE);

			// then
			verify(salesDailyRepository).upsert(eq(SALE_DATE), eq(0L), eq(BigDecimal.ZERO), eq(0L),
					eq(BigDecimal.ZERO), any());
		}

		@Test
		@DisplayName("tombstone 삭제는 UPSERT 가 모두 끝난 뒤에 호출된다")
		void deletesStaleRowsAfterUpsert() {
			// given
			given(salesAggregationQueryMapper.findDailySalesOf(SALE_DATE))
					.willReturn(new DailySalesAggregateRow(1L, BigDecimal.TEN, 0L, BigDecimal.ZERO));
			given(salesAggregationQueryMapper.findProductSalesOf(SALE_DATE))
					.willReturn(List.of(new ProductSalesAggregateRow(1L, 2L, BigDecimal.TEN, 1L)));

			// when
			service.aggregateDate(SALE_DATE);

			// then
			InOrder order = inOrder(salesDailyRepository, salesDailyProductWriter, salesDailyProductRepository);
			order.verify(salesDailyRepository).upsert(eq(SALE_DATE), anyLong(), any(BigDecimal.class), anyLong(),
					any(BigDecimal.class), any());
			order.verify(salesDailyProductWriter).upsertAll(eq(SALE_DATE), anyList(), any());
			order.verify(salesDailyProductRepository).deleteStale(eq(SALE_DATE), any());
		}

		@Test
		@DisplayName("상품 목록이 청크 크기를 넘으면 여러 번 나눠 쓴다")
		void splitsProductWritesIntoChunksWhenExceedingChunkSize() {
			// given
			int rowCount = SalesAggregationService.CHUNK_SIZE + 1;
			List<ProductSalesAggregateRow> rows = LongStream.rangeClosed(1, rowCount)
					.mapToObj(id -> new ProductSalesAggregateRow(id, 1L, BigDecimal.ONE, 1L))
					.toList();
			given(salesAggregationQueryMapper.findDailySalesOf(SALE_DATE))
					.willReturn(new DailySalesAggregateRow(0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO));
			given(salesAggregationQueryMapper.findProductSalesOf(SALE_DATE)).willReturn(rows);

			// when
			service.aggregateDate(SALE_DATE);

			// then
			ArgumentCaptor<List<ProductSalesAggregateRow>> chunkCaptor = ArgumentCaptor.forClass(List.class);
			verify(salesDailyProductWriter, times(2)).upsertAll(eq(SALE_DATE), chunkCaptor.capture(), any());
			List<List<ProductSalesAggregateRow>> chunks = chunkCaptor.getAllValues();
			assertThat(chunks.get(0)).hasSize(SalesAggregationService.CHUNK_SIZE);
			assertThat(chunks.get(1)).hasSize(1);
		}

		@Test
		@DisplayName("상품 판매가 없으면 sales_daily_product 쓰기를 호출하지 않는다")
		void doesNotWriteProductSalesWhenEmpty() {
			// given
			given(salesAggregationQueryMapper.findDailySalesOf(SALE_DATE))
					.willReturn(new DailySalesAggregateRow(0L, BigDecimal.ZERO, 0L, BigDecimal.ZERO));
			given(salesAggregationQueryMapper.findProductSalesOf(SALE_DATE)).willReturn(List.of());

			// when
			service.aggregateDate(SALE_DATE);

			// then
			verify(salesDailyProductWriter, never()).upsertAll(any(), anyList(), any());
			verify(salesDailyProductRepository).deleteStale(eq(SALE_DATE), any());
		}
	}
}
