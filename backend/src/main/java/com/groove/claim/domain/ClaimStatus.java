package com.groove.claim.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 반품(claim) 상태 — 역물류 상태머신. 전이 규칙은 canTransitionTo(ClaimStatus) 가 판정한다.
 *
 * <p>합법 전이 (6종, 그 외는 모두 불법):
 * <pre>
 *   REQUESTED  → APPROVED, REJECTED   (2)  관리자 승인/거부
 *   APPROVED   → IN_TRANSIT           (1)  스케줄러 자동
 *   IN_TRANSIT → INSPECTING           (1)  스케줄러 자동
 *   INSPECTING → REFUNDED, REJECTED   (2)  스케줄러 자동통과+환불 / 관리자 검수 불합격
 *   REFUNDED   → (종착)
 *   REJECTED   → (종착)
 * </pre>
 */
public enum ClaimStatus {
    REQUESTED,
    APPROVED,
    IN_TRANSIT,
    INSPECTING,
    REFUNDED,
    REJECTED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS;

    static {
        EnumMap<ClaimStatus, Set<ClaimStatus>> map = new EnumMap<>(ClaimStatus.class);
        map.put(REQUESTED, EnumSet.of(APPROVED, REJECTED));
        map.put(APPROVED, EnumSet.of(IN_TRANSIT));
        map.put(IN_TRANSIT, EnumSet.of(INSPECTING));
        map.put(INSPECTING, EnumSet.of(REFUNDED, REJECTED));
        map.put(REFUNDED, EnumSet.noneOf(ClaimStatus.class));
        map.put(REJECTED, EnumSet.noneOf(ClaimStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(ClaimStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
