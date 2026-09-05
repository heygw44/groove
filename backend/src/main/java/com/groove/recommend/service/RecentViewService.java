package com.groove.recommend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.product.dto.ProductSummaryResponse;
import com.groove.recommend.mapper.RecommendQueryMapper;

import lombok.RequiredArgsConstructor;

/** 최근 본 상품 조회. Redis 목록을 우선 쓰고, 비어 있으면 DB 조회 로그로 폴백한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentViewService {

	private final RecentViewRedisService recentViewRedisService;

	private final RecommendQueryMapper recommendQueryMapper;

	public List<ProductSummaryResponse> getRecentViews(Long memberId) {
		RecentIds recentIds = loadRecentIds(memberId);
		List<Long> distinctIds = recentIds.ids();
		if (distinctIds.isEmpty()) {
			return List.of();
		}

		Map<Long, ProductSummaryResponse> summariesById = recommendQueryMapper.findSummariesByIds(distinctIds, memberId)
				.stream()
				.collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));

		// HIDDEN·삭제된 상품은 자연히 빠져 결과가 요청한 id 개수보다 적을 수 있다.
		List<ProductSummaryResponse> summaries = distinctIds.stream()
				.map(summariesById::get)
				.filter(Objects::nonNull)
				.toList();

		if (recentIds.fromRedis()) {
			List<Long> staleIds = distinctIds.stream()
					.filter(id -> !summariesById.containsKey(id))
					.toList();
			if (!staleIds.isEmpty()) {
				recentViewRedisService.remove(memberId, staleIds);
			}
		}

		return summaries;
	}

	/** 다른 추천 신호와 조합할 때 요약 조회 없이 id 만 필요한 경우. */
	public List<Long> findRecentProductIds(Long memberId) {
		return loadRecentIds(memberId).ids();
	}

	private RecentIds loadRecentIds(Long memberId) {
		List<Long> ids = recentViewRedisService.findRecentProductIds(memberId);
		boolean fromRedis = !ids.isEmpty();

		// Redis 장애·TTL 만료로 빈 리스트가 오는 경우도 같은 분기로 흡수해 DB 조회 로그로 폴백한다.
		if (ids.isEmpty()) {
			ids = recommendQueryMapper.findRecentProductIds(memberId, RecentViewRedisService.MAX_SIZE);
		}

		return new RecentIds(ids.stream().distinct().toList(), fromRedis);
	}

	private record RecentIds(List<Long> ids, boolean fromRedis) {
	}
}
