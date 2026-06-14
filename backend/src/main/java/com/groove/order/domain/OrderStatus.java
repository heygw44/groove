package com.groove.order.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 주문 상태 (glossary §3.4, ARCHITECTURE.md §8 상태 다이어그램).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(OrderStatus)} 단일 메서드에서 판정한다.
 * DB 트리거를 두지 않고 애플리케이션 레벨 단일 진입점({@link Order#changeStatus})에 일원화한다.
 *
 * <p>합법 전이 (9종, 그 외는 모두 불법):
 * <pre>
 *   PENDING        → PAID, PAYMENT_FAILED, CANCELLED   (3)
 *   PAID           → PREPARING, CANCELLED              (2)
 *   PREPARING      → SHIPPED, CANCELLED                (2)
 *   SHIPPED        → DELIVERED                         (1)
 *   DELIVERED      → COMPLETED                         (1)
 *   COMPLETED      → (종착)
 *   CANCELLED      → (종착)
 *   PAYMENT_FAILED → (종착)
 * </pre>
 */
public enum OrderStatus {
    PENDING,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    PAYMENT_FAILED;

    /**
     * "배송완료 이상" 주문 상태 집합 — 리뷰 작성 자격(#59)과 반품 접수 자격(#239)이 공유하는 경계다.
     * {@code DELIVERED} 또는 {@code COMPLETED} 면 상품이 고객에게 도달했다고 본다.
     */
    public static final Set<OrderStatus> DELIVERED_OR_COMPLETED = Collections.unmodifiableSet(
            EnumSet.of(DELIVERED, COMPLETED));

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

    static {
        EnumMap<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);
        map.put(PENDING, EnumSet.of(PAID, PAYMENT_FAILED, CANCELLED));
        map.put(PAID, EnumSet.of(PREPARING, CANCELLED));
        map.put(PREPARING, EnumSet.of(SHIPPED, CANCELLED));
        map.put(SHIPPED, EnumSet.of(DELIVERED));
        map.put(DELIVERED, EnumSet.of(COMPLETED));
        map.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        map.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        map.put(PAYMENT_FAILED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
