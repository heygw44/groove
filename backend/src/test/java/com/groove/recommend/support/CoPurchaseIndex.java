package com.groove.recommend.support;

import java.util.Map;
import java.util.Set;

/**
 * {@code seedIds} 각각에 대해 "함께 구매한 상품" 점수를 돌려주는 조회 경로. {@link EvalRunner} 가 이 인터페이스로
 * 공동구매 점수를 얻어, 운영 경로({@link RedisCoPurchaseIndex})와 측정용 인메모리 재집계
 * ({@link InMemoryCoPurchaseIndex})를 갈아 끼울 수 있게 한다.
 */
public interface CoPurchaseIndex {

	Map<Long, Map<Long, Double>> findScores(Set<Long> productIds);
}
