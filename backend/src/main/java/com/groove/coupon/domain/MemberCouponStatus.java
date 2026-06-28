package com.groove.coupon.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 회원 보유 쿠폰 상태. 합법 전이는 TRANSITIONS 가 정의.
 * USED → ISSUED 는 주문 취소/환불 복원 경로(이미 만료됐으면 EXPIRED).
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
