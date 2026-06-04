package com.groove.coupon.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 쿠폰 정책 상태 (glossary §3.10, docs/plans/coupon-system.md §3.3).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(CouponStatus)} 단일 메서드에서 판정한다 —
 * {@link com.groove.order.domain.OrderStatus} 와 동일하게 DB 트리거 없이 애플리케이션
 * 레벨 단일 진입점({@link Coupon#changeStatus})에 일원화한다.
 *
 * <p>합법 전이 (4종, 그 외는 모두 불법):
 * <pre>
 *   ACTIVE    → SUSPENDED, ENDED   (2)
 *   SUSPENDED → ACTIVE, ENDED      (2)
 *   ENDED     → (종착)
 * </pre>
 */
public enum CouponStatus {
    ACTIVE,
    SUSPENDED,
    ENDED;

    private static final Map<CouponStatus, Set<CouponStatus>> TRANSITIONS;

    static {
        EnumMap<CouponStatus, Set<CouponStatus>> map = new EnumMap<>(CouponStatus.class);
        map.put(ACTIVE, EnumSet.of(SUSPENDED, ENDED));
        map.put(SUSPENDED, EnumSet.of(ACTIVE, ENDED));
        map.put(ENDED, EnumSet.noneOf(CouponStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(CouponStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
