package com.groove.payment.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 결제 상태 (glossary §3.5, ERD §6).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(PaymentStatus)} 단일 메서드에서 판정한다 —
 * {@code OrderStatus} 와 동일하게 DB 트리거를 두지 않고 애플리케이션 레벨에 일원화한다.
 * {@code Payment} 엔티티 본체와 영속화 매핑은 #W7-3 범위이며, 본 enum 은
 * {@link com.groove.payment.gateway.PaymentGateway#query(String)} 의 반환 타입으로
 * 게이트웨이 계약에 필요해 #W7-1 에서 먼저 도입한다.
 *
 * <p>합법 전이 (3종, 그 외는 모두 불법):
 * <pre>
 *   PENDING  → PAID, FAILED   (2)
 *   PAID     → REFUNDED       (1)
 *   FAILED   → (종착)
 *   REFUNDED → (종착)
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS;

    static {
        EnumMap<PaymentStatus, Set<PaymentStatus>> map = new EnumMap<>(PaymentStatus.class);
        map.put(PENDING, EnumSet.of(PAID, FAILED));
        map.put(PAID, EnumSet.of(REFUNDED));
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
