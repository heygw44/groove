package com.groove.admin.application;

import com.groove.order.domain.OrderStatus;

import java.time.Instant;

/**
 * 관리자 주문 목록 필터 (GET /api/v1/admin/orders). 모든 필드 nullable — null 이면 해당 조건 미적용, 전부 null 이면 전체 조회.
 * from 은 생성 시각 하한(포함), to 는 상한(미포함). memberId 지정 시 게스트 주문은 제외된다.
 */
public record AdminOrderSearchCriteria(OrderStatus status, Long memberId, Instant from, Instant to) {
}
