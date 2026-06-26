package com.groove.coupon.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 쿠폰 정책 상태. 전이 규칙은 canTransitionTo 단일 메서드에서 판정한다. 합법 전이(그 외는 불법):
 * - ACTIVE    → SUSPENDED, ENDED
 * - SUSPENDED → ACTIVE, ENDED
 * - ENDED     → (종착)
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
