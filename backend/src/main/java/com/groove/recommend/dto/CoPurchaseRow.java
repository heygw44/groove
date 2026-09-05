package com.groove.recommend.dto;

/** 같은 주문에 함께 담긴 상품 쌍과 등장 횟수(주문 수). */
public record CoPurchaseRow(Long productId, Long otherProductId, long count) {
}
