package com.groove.coupon.application;

import java.util.Map;
import java.util.Set;

/**
 * 주문번호 조회 읽기 전용 포트. order 가 구현 — coupon→order 역참조를 끊어 슬라이스 단방향 유지.
 */
public interface OrderNumberLookup {

    /** 미존재 orderId 는 빠지며, null 키 조회를 허용하는 맵을 반환한다. */
    Map<Long, String> orderNumbersByIds(Set<Long> orderIds);
}
