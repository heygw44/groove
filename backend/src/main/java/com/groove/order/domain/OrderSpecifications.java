package com.groove.order.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * 관리자 주문 목록 조회({@code GET /api/v1/admin/orders})의 동적 필터를 조합 가능한 {@link Specification}
 * 조각으로 제공한다 (이슈 #69). 호출 측({@code AdminOrderService})은 들어온 필터만 골라
 * {@link Specification#allOf(Iterable)} 로 AND 결합한다 — 없으면 전체 조회.
 *
 * <p>모든 조각은 인덱스 가능한 단일 컬럼({@code status}, {@code member_id}, {@code created_at}) 만 참조한다.
 * 정렬 화이트리스트({@code created_at}) 와 함께 컨트롤러가 입력 경계에서 막으므로 임의 컬럼 노출은 없다.
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

    /** {@code createdAt >= from} — 기간 필터 시작 경계(포함). */
    public static Specification<Order> createdAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /** {@code createdAt < to} — 기간 필터 끝 경계(미포함). 호출 측이 "그 날 자정" 같은 상한을 그대로 넘기기 좋게 반열린 구간으로 둔다. */
    public static Specification<Order> createdAtBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
