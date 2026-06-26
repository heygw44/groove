package com.groove.coupon.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 회원 보유 쿠폰 상태. 전이 규칙은 canTransitionTo 단일 메서드에서 판정하고, 위반 시 MemberCoupon 가드 메서드가
 * IllegalCouponStateTransitionException 을 던진다. 합법 전이(그 외는 불법):
 * - ISSUED → USED, EXPIRED, CANCELLED
 * - USED   → ISSUED, EXPIRED   (주문 취소/환불 시 복원 — 이미 만료됐으면 EXPIRED)
 * - EXPIRED, CANCELLED → (종착)
 */
public enum MemberCouponStatus {
    ISSUED,
    USED,
    EXPIRED,
    CANCELLED;

    private static final Map<MemberCouponStatus, Set<MemberCouponStatus>> TRANSITIONS;

    static {
        EnumMap<MemberCouponStatus, Set<MemberCouponStatus>> map = new EnumMap<>(MemberCouponStatus.class);
        map.put(ISSUED, EnumSet.of(USED, EXPIRED, CANCELLED));
        map.put(USED, EnumSet.of(ISSUED, EXPIRED));
        map.put(EXPIRED, EnumSet.noneOf(MemberCouponStatus.class));
        map.put(CANCELLED, EnumSet.noneOf(MemberCouponStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(MemberCouponStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
