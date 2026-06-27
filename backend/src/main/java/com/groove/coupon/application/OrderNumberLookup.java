package com.groove.coupon.application;

import java.util.Map;
import java.util.Set;

/**
 * 쿠폰이 사용된 주문의 표시용 주문번호를 orderId 로 조회하는 읽기 전용 포트(#349).
 *
 * 주문번호는 order 도메인이 소유하므로 coupon 은 이 인터페이스만 의존하고 order 가 구현한다 —
 * coupon→order 역참조를 끊어 슬라이스 단방향(order→coupon)을 유지한다.
 */
public interface OrderNumberLookup {

    /** orderId → orderNumber 맵. 미존재 orderId 는 맵에서 빠지며, null 키 조회를 허용하는 맵을 반환한다. */
    Map<Long, String> orderNumbersByIds(Set<Long> orderIds);
}
