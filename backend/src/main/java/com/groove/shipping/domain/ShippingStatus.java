package com.groove.shipping.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 배송 상태 (ERD §4.13, glossary §3.6).
 *
 * <p>전이 규칙은 {@link #canTransitionTo(ShippingStatus)} 단일 메서드에서 판정한다 —
 * {@code OrderStatus}/{@code PaymentStatus} 와 동일하게 DB 트리거를 두지 않고 애플리케이션 레벨에 일원화한다.
 * 실제 전이는 시연용 자동 진행 스케줄러({@code ShippingProgressScheduler})가 일정 시간 간격으로 한 단계씩 밀어준다.
 *
 * <p>합법 전이 (4종, 그 외는 모두 불법):
 * <pre>
 *   PREPARING → SHIPPED    (1)
 *   PREPARING → CANCELLED  (1)   발송 전 취소·환불 동기화 (#233)
 *   SHIPPED   → DELIVERED  (1)
 *   SHIPPED   → CANCELLED  (1)   #239 반품 플로우 대비 (refund 는 SHIPPED 이후를 막으므로 현 경로에선 미사용)
 *   DELIVERED → (종착)
 *   CANCELLED → (종착)
 * </pre>
 *
 * <p>CANCELLED 는 발송 전(PREPARING) 취소·환불 시 {@code Shipping.cancel()} 로 전이해 자동 진행 스케줄러가 더 이상
 * 밀지 않도록 한다 (#233) — 취소 상태 부재로 환불된 주문 배송이 DELIVERED 까지 진행되던 결함을 막는다.
 */
public enum ShippingStatus {
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Map<ShippingStatus, Set<ShippingStatus>> TRANSITIONS;

    static {
        EnumMap<ShippingStatus, Set<ShippingStatus>> map = new EnumMap<>(ShippingStatus.class);
        map.put(PREPARING, EnumSet.of(SHIPPED, CANCELLED));
        map.put(SHIPPED, EnumSet.of(DELIVERED, CANCELLED));
        map.put(DELIVERED, EnumSet.noneOf(ShippingStatus.class));
        map.put(CANCELLED, EnumSet.noneOf(ShippingStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    public boolean canTransitionTo(ShippingStatus next) {
        if (next == null) {
            return false;
        }
        return TRANSITIONS.get(this).contains(next);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
