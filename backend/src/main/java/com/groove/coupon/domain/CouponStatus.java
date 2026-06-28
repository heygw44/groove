package com.groove.coupon.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 쿠폰 정책 상태. 합법 전이는 TRANSITIONS 가 정의하고 그 외는 불법. */
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
