package com.groove.recommend.support;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 주문 바스켓에서 회원별 홀드아웃 상품을 뺀 뒤 {@code BoughtTogetherAggregator.groupByProduct()} 와 같은
 * 규칙(같은 주문에 함께 담긴 상품 쌍의 등장 주문 수)으로 재집계하는 공동구매 인덱스.
 *
 * <p>재집계는 생성자에서 한 번만 한다 — {@code holdoutByMemberId} 가 곧 (kind, seed, fold) 하나를 뜻하므로,
 * 이 인스턴스는 그 조합 안에서만 재사용해야 한다. ablation 처럼 가중치 구성이 여럿이어도 같은 (kind, seed,
 * fold) 라면 인스턴스 하나를 공유해도 되지만, 회원 단위나 가중치 구성 단위로 나눠 만들면 다른 폴드의
 * 홀드아웃이 섞여 다시 누수가 된다.
 */
public class InMemoryCoPurchaseIndex implements CoPurchaseIndex {

	private final Map<Long, Map<Long, Double>> scoresByProduct;

	public InMemoryCoPurchaseIndex(List<CoPurchaseBasket> baskets, Map<Long, Set<Long>> holdoutByMemberId) {
		this.scoresByProduct = aggregate(baskets, holdoutByMemberId);
	}

	@Override
	public Map<Long, Map<Long, Double>> findScores(Set<Long> productIds) {
		Map<Long, Map<Long, Double>> result = new LinkedHashMap<>();
		for (Long productId : productIds) {
			result.put(productId, scoresByProduct.getOrDefault(productId, Map.of()));
		}
		return result;
	}

	private Map<Long, Map<Long, Double>> aggregate(List<CoPurchaseBasket> baskets,
			Map<Long, Set<Long>> holdoutByMemberId) {
		Map<Long, Map<Long, Long>> countsByProduct = new HashMap<>();
		for (CoPurchaseBasket basket : baskets) {
			Set<Long> holdout = holdoutByMemberId.getOrDefault(basket.memberId(), Set.of());
			List<Long> remainingProductIds = basket.productIds().stream()
					.filter(productId -> !holdout.contains(productId))
					.toList();
			addPairs(countsByProduct, remainingProductIds);
		}
		return toScores(countsByProduct);
	}

	/** 바스켓 안의 모든 (product, otherProduct) 순서쌍에 1을 더한다. 같은 주문은 한 쌍에 최대 1씩만 더한다. */
	private void addPairs(Map<Long, Map<Long, Long>> countsByProduct, List<Long> productIds) {
		for (Long productId : productIds) {
			for (Long otherProductId : productIds) {
				if (productId.equals(otherProductId)) {
					continue;
				}
				countsByProduct.computeIfAbsent(productId, key -> new HashMap<>()).merge(otherProductId, 1L,
						Long::sum);
			}
		}
	}

	private Map<Long, Map<Long, Double>> toScores(Map<Long, Map<Long, Long>> countsByProduct) {
		Map<Long, Map<Long, Double>> scores = new HashMap<>();
		countsByProduct.forEach((productId, countsByOther) -> {
			Map<Long, Double> doubleScores = new HashMap<>();
			countsByOther.forEach((otherProductId, count) -> doubleScores.put(otherProductId, count.doubleValue()));
			scores.put(productId, doubleScores);
		});
		return scores;
	}
}
