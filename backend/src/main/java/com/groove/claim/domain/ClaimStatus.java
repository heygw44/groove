package com.groove.claim.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 반품(claim) 상태 — 역물류 상태머신 (#239).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(ClaimStatus)} 단일 메서드에서 판정한다 —
 * {@code OrderStatus}/{@code ShippingStatus}/{@code PaymentStatus} 와 동일하게 DB 트리거를 두지 않고
 * 애플리케이션 레벨에 일원화한다. {@code OrderStatus} 에 반품 상태를 섞으면 상태 폭발이 일어나므로 주문/품목을
 * 참조하는 별도 aggregate({@code Claim}) 의 자체 상태머신으로 분리한다.
 *
 * <p>합법 전이 (6종, 그 외는 모두 불법):
 * <pre>
 *   REQUESTED  → APPROVED, REJECTED   (2)  관리자 승인/거부
 *   APPROVED   → IN_TRANSIT           (1)  스케줄러 자동(회수 시작 시뮬레이션)
 *   IN_TRANSIT → INSPECTING           (1)  스케줄러 자동(회수 완료→검수)
 *   INSPECTING → REFUNDED, REJECTED   (2)  스케줄러 자동통과+환불 / 관리자 검수 불합격
 *   REFUNDED   → (종착)
 *   REJECTED   → (종착)
 * </pre>
 *
 * <p>회수(IN_TRANSIT)·검수(INSPECTING) 단계 진행과 검수 자동통과+환불은 실제 택배사 연동이 없는 시연 환경에서
 * {@code ClaimProgressScheduler} 가 시간 경과로 자동 진행한다 ({@code ShippingProgressScheduler} 패턴). 검수
 * 불합격(INSPECTING → REJECTED)만 관리자 수동 판단이다.
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
