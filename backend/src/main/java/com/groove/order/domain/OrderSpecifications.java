package com.groove.order.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** 관리자 주문 목록 조회의 동적 필터 Specification 조각. */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasMemberId(Long memberId) {
        return (root, query, cb) -> cb.equal(root.get("memberId"), memberId);
    }

    /** 시작 경계 포함. */
    public static Specification<Order> createdAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /** 끝 경계 미포함. */
    public static Specification<Order> createdAtBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
