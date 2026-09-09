package com.groove.stats.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groove.stats.dto.DailySalesAggregateRow;
import com.groove.stats.dto.ProductSalesAggregateRow;
import com.groove.stats.dto.ProductSalesTotals;
import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;
import com.groove.stats.entity.SalesDaily;
import com.groove.stats.mapper.SalesAggregationQueryMapper;
import com.groove.stats.repository.SalesDailyProductRepository;
import com.groove.stats.repository.SalesDailyRepository;

import lombok.RequiredArgsConstructor;

/**
 * 원본을 재조회해 사전 집계 테이블 값과 하루 단위로 비교한다. 클래스에 {@code @Transactional(readOnly = true)}
 * 를 걸어 원본 재조회와 집계 테이블 조회가 한 번의 짧은 읽기 트랜잭션 안에서 끝나게 한다.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesReconcileComparator {

	private static final BigDecimal AMOUNT_WARN_RATIO = new BigDecimal("0.001");
	private static final BigDecimal AMOUNT_WARN_ABS_LIMIT = new BigDecimal("10000");

	private final SalesAggregationQueryMapper salesAggregationQueryMapper;
	private final SalesDailyRepository salesDailyRepository;
	private final SalesDailyProductRepository salesDailyProductRepository;

	public List<MetricDiff> diff(LocalDate saleDate) {
		List<MetricDiff> diffs = new ArrayList<>();
		diffs.addAll(diffDailySales(saleDate));
		diffs.addAll(diffProductSales(saleDate));
		return diffs;
	}

	private List<MetricDiff> diffDailySales(LocalDate saleDate) {
		DailySalesAggregateRow expected = salesAggregationQueryMapper.findDailySalesOf(saleDate);
		SalesDaily aggregate = salesDailyRepository.findById(saleDate).orElse(null);

		long actualOrderCount = aggregate == null ? 0L : aggregate.getOrderCount();
		BigDecimal actualSalesAmount = aggregate == null ? BigDecimal.ZERO : aggregate.getSalesAmount();
		long actualCancelCount = aggregate == null ? 0L : aggregate.getCancelCount();
		BigDecimal actualCancelAmount = aggregate == null ? BigDecimal.ZERO : aggregate.getCancelAmount();

		List<MetricDiff> diffs = new ArrayList<>();
		addIfMismatch(diffs, ReconcileMetric.DAILY_ORDER_COUNT, false, BigDecimal.valueOf(expected.orderCount()),
				BigDecimal.valueOf(actualOrderCount));
		addIfMismatch(diffs, ReconcileMetric.DAILY_SALES_AMOUNT, true, expected.salesAmount(), actualSalesAmount);
		addIfMismatch(diffs, ReconcileMetric.DAILY_CANCEL_COUNT, false, BigDecimal.valueOf(expected.cancelCount()),
				BigDecimal.valueOf(actualCancelCount));
		addIfMismatch(diffs, ReconcileMetric.DAILY_CANCEL_AMOUNT, true, expected.cancelAmount(), actualCancelAmount);
		return diffs;
	}

	private List<MetricDiff> diffProductSales(LocalDate saleDate) {
		List<ProductSalesAggregateRow> rows = salesAggregationQueryMapper.findProductSalesOf(saleDate);
		long expectedSoldQuantity = rows.stream().mapToLong(ProductSalesAggregateRow::soldQuantity).sum();
		BigDecimal expectedSalesAmount = rows.stream()
				.map(ProductSalesAggregateRow::salesAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		long expectedOrderCount = rows.stream().mapToLong(ProductSalesAggregateRow::orderCount).sum();

		ProductSalesTotals actual = salesDailyProductRepository.sumBySaleDate(saleDate);

		List<MetricDiff> diffs = new ArrayList<>();
		addIfMismatch(diffs, ReconcileMetric.PRODUCT_SOLD_QUANTITY, false, BigDecimal.valueOf(expectedSoldQuantity),
				BigDecimal.valueOf(actual.soldQuantity()));
		addIfMismatch(diffs, ReconcileMetric.PRODUCT_SALES_AMOUNT, true, expectedSalesAmount, actual.salesAmount());
		addIfMismatch(diffs, ReconcileMetric.PRODUCT_ORDER_COUNT, false, BigDecimal.valueOf(expectedOrderCount),
				BigDecimal.valueOf(actual.orderCount()));
		return diffs;
	}

	private void addIfMismatch(List<MetricDiff> diffs, ReconcileMetric metric, boolean isAmount, BigDecimal expected,
			BigDecimal actual) {
		if (expected.compareTo(actual) == 0) {
			return;
		}
		diffs.add(new MetricDiff(metric, resolveSeverity(isAmount, expected, actual), expected, actual));
	}

	// 건수 지표는 정수라 0 이 아닌 차이는 항상 절대값 1 이상이다 — WARN 구간 없이 바로 CRITICAL 이다.
	private ReconcileSeverity resolveSeverity(boolean isAmount, BigDecimal expected, BigDecimal actual) {
		if (!isAmount) {
			return ReconcileSeverity.CRITICAL;
		}
		BigDecimal diff = actual.subtract(expected).abs();
		if (diff.compareTo(AMOUNT_WARN_ABS_LIMIT) >= 0) {
			return ReconcileSeverity.CRITICAL;
		}
		// expected 가 0 이면 비율이 정의되지 않는다. 0.1% 조건을 만족할 방법이 없으니 CRITICAL 로 본다.
		if (expected.compareTo(BigDecimal.ZERO) == 0) {
			return ReconcileSeverity.CRITICAL;
		}
		BigDecimal ratio = diff.divide(expected.abs(), 10, RoundingMode.HALF_UP);
		return ratio.compareTo(AMOUNT_WARN_RATIO) < 0 ? ReconcileSeverity.WARN : ReconcileSeverity.CRITICAL;
	}
}
