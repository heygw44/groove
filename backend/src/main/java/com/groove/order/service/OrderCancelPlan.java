package com.groove.order.service;

import java.util.List;

/**
 * {@link OrderCancelWriter#planCancel} 의 결과. {@code eligibleForFullCancel} 이 true 일 때만 기존
 * 전액취소 경로(결제 전체를 한 번에 취소)를 쓸 수 있다 - 상품주문이 전부 PAID 이고 클레임 이력이 전혀 없고
 * 결제가 DONE·CANCEL_REQUESTED 일 때만이다. 그 외(PREPARING 이 섞였거나, 이미 취소·반품된 상품이 있거나,
 * 결제가 PARTIAL_CANCELED 인 경우)는 {@code cancelableItemIds} 를 상품 단위 클레임 경로로 하나씩 처리해야 한다.
 */
public record OrderCancelPlan(List<Long> cancelableItemIds, boolean eligibleForFullCancel) {
}
