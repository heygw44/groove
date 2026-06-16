package com.groove.order.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * 관리자 주문 목록 조회의 동적 필터를 조합 가능한 Specification 조각으로 제공한다.
 * 각 조각은 단일 컬럼(status, member_id, created_at)만 참조한다.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasMemberId(Long memberId) {
        return (root, query, cb) -> cb.equal(root.get("memberId"), memberId);
    }

    /** createdAt >= from — 기간 필터 시작 경계(포함). */
    public static Specification<Order> createdAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /** createdAt < to — 기간 필터 끝 경계(미포함). */
    public static Specification<Order> createdAtBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
