package com.groove.shipping.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 배송 상태 (ERD §4.13, glossary §3.6).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(ShippingStatus)} 단일 메서드에서 판정한다 —
 * {@code OrderStatus}/{@code PaymentStatus} 와 동일하게 DB 트리거를 두지 않고 애플리케이션 레벨에 일원화한다.
 * 실제 전이는 시연용 자동 진행 스케줄러({@code ShippingProgressScheduler})가 일정 시간 간격으로 한 단계씩 밀어준다.
 *
 * <p>합법 전이 (2종, 그 외는 모두 불법):
 * <pre>
 *   PREPARING → SHIPPED    (1)
 *   SHIPPED   → DELIVERED  (1)
 *   DELIVERED → (종착)
 * </pre>
 */
public enum ShippingStatus {
    PREPARING,
    SHIPPED,
    DELIVERED;

    private static final Map<ShippingStatus, Set<ShippingStatus>> TRANSITIONS;

    static {
        EnumMap<ShippingStatus, Set<ShippingStatus>> map = new EnumMap<>(ShippingStatus.class);
        map.put(PREPARING, EnumSet.of(SHIPPED));
        map.put(SHIPPED, EnumSet.of(DELIVERED));
        map.put(DELIVERED, EnumSet.noneOf(ShippingStatus.class));
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
