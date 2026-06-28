package com.groove.claim.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 반품(claim) 역물류 상태머신. 합법 전이는 TRANSITIONS 가 정의.
 * 트리거: REQUESTED 분기는 관리자, APPROVED→IN_TRANSIT→INSPECTING 은 스케줄러, INSPECTING 분기는 자동통과/관리자 불합격.
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
