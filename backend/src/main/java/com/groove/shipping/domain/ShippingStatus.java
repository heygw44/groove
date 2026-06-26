package com.groove.shipping.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 배송 상태. 전이 규칙은 canTransitionTo(ShippingStatus) 단일 메서드에서 판정한다.
 *
 * 합법 전이 (그 외는 모두 불법):
 *   PREPARING → SHIPPED, CANCELLED
 *   SHIPPED   → DELIVERED, CANCELLED
 *   DELIVERED → (종착)
 *   CANCELLED → (종착)
 */
public enum ShippingStatus {
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<ShippingStatus, Set<ShippingStatus>> TRANSITIONS;

    static {
        EnumMap<ShippingStatus, Set<ShippingStatus>> map = new EnumMap<>(ShippingStatus.class);
        map.put(PREPARING, EnumSet.of(SHIPPED, CANCELLED));
        map.put(SHIPPED, EnumSet.of(DELIVERED, CANCELLED));
        map.put(DELIVERED, EnumSet.noneOf(ShippingStatus.class));
        map.put(CANCELLED, EnumSet.noneOf(ShippingStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(ShippingStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
