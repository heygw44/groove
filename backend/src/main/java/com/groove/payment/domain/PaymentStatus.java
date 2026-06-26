package com.groove.payment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 결제 상태. 전이 규칙은 canTransitionTo(PaymentStatus) 가 판정한다(그 외는 모두 불법).
 * - PENDING → PAID, FAILED
 * - PAID → PARTIALLY_REFUNDED, REFUNDED
 * - PARTIALLY_REFUNDED → REFUNDED
 * - FAILED, REFUNDED → 종착
 * PARTIALLY_REFUNDED 는 일부 환불 상태로, 누적 환불액이 전액에 도달하면 REFUNDED 로 전이한다.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS;

    static {
        EnumMap<PaymentStatus, Set<PaymentStatus>> map = new EnumMap<>(PaymentStatus.class);
        map.put(PENDING, EnumSet.of(PAID, FAILED));
        map.put(PAID, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED));
        map.put(PARTIALLY_REFUNDED, EnumSet.of(REFUNDED));
        map.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        map.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(PaymentStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
