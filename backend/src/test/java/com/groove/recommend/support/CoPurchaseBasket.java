package com.groove.recommend.support;

import java.util.Set;

/** 주문 하나의 바스켓. {@link InMemoryCoPurchaseIndex} 가 회원별 홀드아웃을 빼고 재집계할 때 쓴다. */
public record CoPurchaseBasket(Long orderId, Long memberId, Set<Long> productIds) {
}
