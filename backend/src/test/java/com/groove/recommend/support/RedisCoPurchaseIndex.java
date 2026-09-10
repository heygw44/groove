package com.groove.recommend.support;

import java.util.Map;
import java.util.Set;

import com.groove.recommend.service.BoughtTogetherRedisService;

/**
 * {@link BoughtTogetherRedisService} 위임. {@code RecommendService.recommendHome()} 이 쓰는 경로와
 * 같아야 하므로, {@code harnessMatchesRecommendHome()} 처럼 운영 경로와 결과가 같은지 확인하는 테스트만
 * 이걸 쓴다. 나머지 측정(재현율 등)은 홀드아웃 누수를 피해야 하므로 {@link InMemoryCoPurchaseIndex} 를 쓴다.
 */
public class RedisCoPurchaseIndex implements CoPurchaseIndex {

	private final BoughtTogetherRedisService boughtTogetherRedisService;

	public RedisCoPurchaseIndex(BoughtTogetherRedisService boughtTogetherRedisService) {
		this.boughtTogetherRedisService = boughtTogetherRedisService;
	}

	@Override
	public Map<Long, Map<Long, Double>> findScores(Set<Long> productIds) {
		return boughtTogetherRedisService.findScores(productIds);
	}
}
