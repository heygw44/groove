package com.groove.recommend.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.groove.order.entity.OrderStatus;

import jakarta.persistence.EntityManager;

/**
 * 주문 바스켓(orderId → memberId, 상품 id 목록)을 DB 에서 한 번 읽는다.
 * {@code BoughtTogetherAggregator.refresh()} 와 같은 조건(PAID 이상 상태, 최근 365일)을 써야
 * {@link InMemoryCoPurchaseIndex} 가 Redis 집계를 올바르게 재현한다.
 */
public final class CoPurchaseBasketLoader {

	private static final int WINDOW_DAYS = 365;

	private CoPurchaseBasketLoader() {
	}

	public static List<CoPurchaseBasket> load(EntityManager entityManager, Clock clock) {
		LocalDateTime sinceAt = LocalDateTime.now(clock).minusDays(WINDOW_DAYS);
		List<Object[]> rows = entityManager.createQuery(
				"select oi.order.id, oi.order.member.id, oi.product.id from OrderItem oi "
						+ "where oi.order.status in :statuses and oi.order.createdAt >= :sinceAt", Object[].class)
				.setParameter("statuses", OrderStatus.PAID_OR_LATER)
				.setParameter("sinceAt", sinceAt)
				.getResultList();

		Map<Long, Long> memberIdByOrderId = new LinkedHashMap<>();
		Map<Long, Set<Long>> productIdsByOrderId = new LinkedHashMap<>();
		for (Object[] row : rows) {
			Long orderId = (Long)row[0];
			Long memberId = (Long)row[1];
			Long productId = (Long)row[2];
			memberIdByOrderId.putIfAbsent(orderId, memberId);
			productIdsByOrderId.computeIfAbsent(orderId, key -> new LinkedHashSet<>()).add(productId);
		}

		List<CoPurchaseBasket> baskets = new ArrayList<>();
		for (Map.Entry<Long, Set<Long>> entry : productIdsByOrderId.entrySet()) {
			Long orderId = entry.getKey();
			baskets.add(new CoPurchaseBasket(orderId, memberIdByOrderId.get(orderId), entry.getValue()));
		}
		return baskets;
	}
}
