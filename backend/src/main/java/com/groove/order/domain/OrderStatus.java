package com.groove.order.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 주문 상태. 전이 규칙은 canTransitionTo 단일 메서드에서 판정한다.
 *
 * 합법 전이 (그 외는 모두 불법):
 *   PENDING        → PAID, PAYMENT_FAILED, CANCELLED
 *   PAID           → PREPARING, CANCELLED
 *   PREPARING      → SHIPPED, CANCELLED
 *   SHIPPED        → DELIVERED
 *   DELIVERED      → COMPLETED
 *   COMPLETED / CANCELLED / PAYMENT_FAILED → (종착)
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

    /** "배송완료 이상" 주문 상태 집합 — DELIVERED 또는 COMPLETED. */
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
