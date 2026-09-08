package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.stats.dto.DailySalesAggregateRow;
import com.groove.stats.dto.ProductSalesAggregateRow;
import com.groove.stats.dto.ProductSalesTotals;
import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;
import com.groove.stats.entity.SalesDaily;
import com.groove.stats.mapper.SalesAggregationQueryMapper;
import com.groove.stats.repository.SalesDailyProductRepository;
import com.groove.stats.repository.SalesDailyRepository;

@ExtendWith(MockitoExtension.class)
class SalesReconcileComparatorTest {

	private static final LocalDate SALE_DATE = LocalDate.of(2031, 3, 15);

	@Mock
	private SalesAggregationQueryMapper salesAggregationQueryMapper;

	@Mock
	private SalesDailyRepository salesDailyRepository;

	@Mock
	private SalesDailyProductRepository salesDailyProductRepository;

	private SalesReconcileComparator comparator;

	private void setUp() {
		comparator = new SalesReconcileComparator(salesAggregationQueryMapper, salesDailyRepository,
				salesDailyProductRepository);
	}

	private void stubNoProductSales() {
		given(salesAggregationQueryMapper.findProductSalesOf(SALE_DATE)).willReturn(List.of());
		given(salesDailyProductRepository.sumBySaleDate(SALE_DATE))
				.willReturn(new ProductSalesTotals(0L, BigDecimal.ZERO, 0L));
	}

	private void stubDailySales(long orderCount, BigDecimal actualAmount, BigDecimal expectedAmount) {
		given(salesAggregationQueryMapper.findDailySalesOf(SALE_DATE))
				.willReturn(new DailySalesAggregateRow(orderCount, expectedAmount, 0L, BigDecimal.ZERO));
		given(salesDailyRepository.findById(SALE_DATE)).willReturn(Optional.of(
				SalesDaily.of(SALE_DATE, orderCount, actualAmount, 0L, BigDecimal.ZERO, LocalDateTime.now())));
	}

	@Nested
	@DisplayName("diff() — 금액 지표 임계치")
	class AmountThreshold {

		@ParameterizedTest(name = "expected={0}, actual={1} -> {2}")
		@DisplayName("금액 지표는 비율<0.1%·절대값<10000 이면 WARN, 그 외 불일치는 CRITICAL 이다")
		@CsvSource({
			"1000000, 1000000, NONE",
			"1000000, 1000900, WARN",
			"1000000, 1001000, CRITICAL",
			"1000000, 1011000, CRITICAL",
			"500000, 502000, CRITICAL",
			"100000, 90000, CRITICAL",
			"0, 1, CRITICAL"
		})
		void classifiesByAmountThreshold(BigDecimal expectedAmount, BigDecimal actualAmount, String expectedResult) {
			// given
			setUp();
			stubDailySales(1L, actualAmount, expectedAmount);
			stubNoProductSales();

			// when
			List<MetricDiff> diffs = comparator.diff(SALE_DATE);

			// then
			if ("NONE".equals(expectedResult)) {
				assertThat(diffs).isEmpty();
				return;
			}
			MetricDiff salesAmountDiff = diffs.stream()
					.filter(diff -> diff.metric() == ReconcileMetric.DAILY_SALES_AMOUNT)
					.findFirst()
					.orElseThrow();
			assertThat(salesAmountDiff.severity()).isEqualTo(ReconcileSeverity.valueOf(expectedResult));
		}
	}

	@Nested
	@DisplayName("diff() — 건수 지표")
	class CountMetric {

		@Test
		@DisplayName("건수 지표는 0 이 아닌 차이면 WARN 없이 바로 CRITICAL 이다")
		void alwaysCriticalWhenNonZero() {
			// given
			setUp();
			stubDailySales(3L, BigDecimal.ZERO, BigDecimal.ZERO);
			given(salesDailyRepository.findById(SALE_DATE)).willReturn(Optional.of(
					SalesDaily.of(SALE_DATE, 2L, BigDecimal.ZERO, 0L, BigDecimal.ZERO, LocalDateTime.now())));
			stubNoProductSales();

			// when
			List<MetricDiff> diffs = comparator.diff(SALE_DATE);

			// then
			MetricDiff orderCountDiff = diffs.stream()
					.filter(diff -> diff.metric() == ReconcileMetric.DAILY_ORDER_COUNT)
					.findFirst()
					.orElseThrow();
			assertThat(orderCountDiff.severity()).isEqualTo(ReconcileSeverity.CRITICAL);
		}

		@Test
		@DisplayName("건수가 같으면 기록하지 않는다")
		void skipsWhenEqual() {
			// given
			setUp();
			stubDailySales(2L, BigDecimal.ZERO, BigDecimal.ZERO);
			stubNoProductSales();

			// when
			List<MetricDiff> diffs = comparator.diff(SALE_DATE);

			// then
			assertThat(diffs).noneMatch(diff -> diff.metric() == ReconcileMetric.DAILY_ORDER_COUNT);
		}
	}

	@Nested
	@DisplayName("diff() — sales_daily 행이 없는 날짜")
	class MissingAggregateRow {

		@Test
		@DisplayName("집계 행이 없으면 0 으로 간주해 비교한다")
		void treatsMissingRowAsZero() {
			// given
			setUp();
			given(salesAggregationQueryMapper.findDailySalesOf(SALE_DATE))
					.willReturn(new DailySalesAggregateRow(1L, new BigDecimal("10000"), 0L, BigDecimal.ZERO));
			given(salesDailyRepository.findById(SALE_DATE)).willReturn(Optional.empty());
			stubNoProductSales();

			// when
			List<MetricDiff> diffs = comparator.diff(SALE_DATE);

			// then
			assertThat(diffs).anyMatch(diff -> diff.metric() == ReconcileMetric.DAILY_ORDER_COUNT
					&& diff.actualValue().compareTo(BigDecimal.ZERO) == 0);
		}
	}

	@Nested
	@DisplayName("diff() — 상품 합계")
	class ProductTotals {

		@Test
		@DisplayName("상품 판매 원본 합계와 sales_daily_product 합계가 다르면 기록한다")
		void detectsProductTotalMismatch() {
			// given
			setUp();
			stubDailySales(0L, BigDecimal.ZERO, BigDecimal.ZERO);
			given(salesAggregationQueryMapper.findProductSalesOf(SALE_DATE)).willReturn(
					List.of(new ProductSalesAggregateRow(1L, 5L, new BigDecimal("50000"), 2L)));
			given(salesDailyProductRepository.sumBySaleDate(SALE_DATE))
					.willReturn(new ProductSalesTotals(3L, new BigDecimal("30000"), 2L));

			// when
			List<MetricDiff> diffs = comparator.diff(SALE_DATE);

			// then
			assertThat(diffs).anyMatch(diff -> diff.metric() == ReconcileMetric.PRODUCT_SOLD_QUANTITY
					&& diff.severity() == ReconcileSeverity.CRITICAL);
			assertThat(diffs).noneMatch(diff -> diff.metric() == ReconcileMetric.PRODUCT_ORDER_COUNT);
		}
	}
}
