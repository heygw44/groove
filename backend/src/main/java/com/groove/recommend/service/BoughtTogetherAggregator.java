package com.groove.recommend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.recommend.dto.CoPurchaseRow;
import com.groove.recommend.mapper.RecommendQueryMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 최근 {@value #WINDOW_DAYS}일 주문 내역에서 공동구매 점수를 집계해 Redis 에 적재한다. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BoughtTogetherAggregator {

	static final int WINDOW_DAYS = 365;

	private final RecommendQueryMapper recommendQueryMapper;
	private final BoughtTogetherRedisService boughtTogetherRedisService;
	private final Clock clock;

	/** 집계 후 Redis 에 적재한 상품 수를 반환한다. */
	public int refresh() {
		LocalDateTime sinceAt = LocalDateTime.now(clock).minusDays(WINDOW_DAYS);
		List<CoPurchaseRow> rows = recommendQueryMapper.countCoPurchases(sinceAt);
		Map<Long, Map<Long, Long>> countsByProduct = groupByProduct(rows);
		if (countsByProduct.isEmpty()) {
			return 0;
		}
		boughtTogetherRedisService.replaceAll(countsByProduct);
		log.info("공동구매 집계 완료 sinceAt={} productCount={}", sinceAt, countsByProduct.size());
		return countsByProduct.size();
	}

	private Map<Long, Map<Long, Long>> groupByProduct(List<CoPurchaseRow> rows) {
		Map<Long, Map<Long, Long>> countsByProduct = new HashMap<>();
		for (CoPurchaseRow row : rows) {
			countsByProduct.computeIfAbsent(row.productId(), key -> new HashMap<>())
					.put(row.otherProductId(), row.count());
		}
		return countsByProduct;
	}
}
