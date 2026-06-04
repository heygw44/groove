package com.groove.coupon.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 회원 보유 쿠폰 상태 (glossary §3.11, docs/plans/coupon-system.md §3.3).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(MemberCouponStatus)} 단일 메서드에서 판정한다.
 * 위반 시 {@link MemberCoupon} 의 가드 전이 메서드가
 * {@link com.groove.coupon.exception.IllegalCouponStateTransitionException} 을 던진다.
 *
 * <p>합법 전이 (4종, 그 외는 모두 불법):
 * <pre>
 *   ISSUED → USED, EXPIRED, CANCELLED   (3)
 *   USED   → ISSUED                     (1, 주문 취소/환불 시 복원)
 *   EXPIRED   → (종착)
 *   CANCELLED → (종착)
 * </pre>
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
        map.put(USED, EnumSet.of(ISSUED));
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
