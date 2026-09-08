package com.groove.stats.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.stats.dto.DailySalesAggregateRow;
import com.groove.stats.dto.ProductSalesAggregateRow;
import com.groove.stats.mapper.SalesAggregationQueryMapper;
import com.groove.stats.repository.SalesDailyProductRepository;
import com.groove.stats.repository.SalesDailyRepository;

import lombok.RequiredArgsConstructor;

/**
 * 사전 집계 테이블을 하루 단위로 재집계한다. 원본을 {@code INSERT ... SELECT} 로 옮기면 REPEATABLE READ 에서
 * 소스 테이블에 공유 넥스트키 락이 걸려 그 시간대 결제 승인이 대기한다. 대신 읽기는 MyBatis 로 하루치만 힙에
 * 올리고, 쓰기는 UPSERT 로 분리해 소스 테이블에 어떤 락도 남기지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesAggregationService {

	static final int CHUNK_SIZE = 1000;

	private final SalesAggregationQueryMapper salesAggregationQueryMapper;
	private final SalesDailyRepository salesDailyRepository;
	private final SalesDailyProductRepository salesDailyProductRepository;
	private final SalesDailyProductWriter salesDailyProductWriter;
	private final Clock clock;

	@Transactional
	public void aggregateDate(LocalDate saleDate) {
		LocalDateTime runAt = LocalDateTime.now(clock);

		upsertDailySales(saleDate, runAt);
		upsertProductSales(saleDate, runAt);

		// 그날 팔렸다가 전부 취소돼 원본 GROUP BY 에서 사라진 상품의 잔존 행만 지운다.
		salesDailyProductRepository.deleteStale(saleDate, runAt);
	}

	private void upsertDailySales(LocalDate saleDate, LocalDateTime runAt) {
		// 매퍼는 결제 0건인 날도 0 값 1행을 항상 돌려줘 별도의 0 리셋 없이 이 UPSERT 만으로 처리된다.
		DailySalesAggregateRow row = salesAggregationQueryMapper.findDailySalesOf(saleDate);
		salesDailyRepository.upsert(saleDate, row.orderCount(), row.salesAmount(), row.cancelCount(),
				row.cancelAmount(), runAt);
	}

	private void upsertProductSales(LocalDate saleDate, LocalDateTime runAt) {
		// 매퍼가 이미 product_id 오름차순으로 반환한다. ProductSalesStatsUpdater 와 같은 데드락 회피 규칙이라
		// 청크로 나눠도 순서를 그대로 유지해야 한다.
		List<ProductSalesAggregateRow> rows = salesAggregationQueryMapper.findProductSalesOf(saleDate);
		for (int from = 0; from < rows.size(); from += CHUNK_SIZE) {
			int to = Math.min(from + CHUNK_SIZE, rows.size());
			salesDailyProductWriter.upsertAll(saleDate, rows.subList(from, to), runAt);
		}
	}
}
