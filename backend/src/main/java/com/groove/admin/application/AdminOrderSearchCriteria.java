package com.groove.admin.application;

import com.groove.order.domain.OrderStatus;

import java.time.Instant;

/**
 * 관리자 주문 목록 필터 (이슈 #69, GET /api/v1/admin/orders). 모든 필드 nullable — null 이면 해당 조건 미적용,
 * 전부 null 이면 전체 조회. 컨트롤러가 입력값을 바인딩/검증해 만들어 넘긴다.
 *
 * @param status   주문 상태
 * @param memberId 회원 id (지정 시 게스트 주문은 제외됨)
 * @param from     생성 시각 하한(포함)
 * @param to       생성 시각 상한(미포함)
 */
public record AdminOrderSearchCriteria(OrderStatus status, Long memberId, Instant from, Instant to) {
}
