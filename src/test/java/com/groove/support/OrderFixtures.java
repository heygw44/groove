package com.groove.support;

import com.groove.order.api.dto.ShippingInfoRequest;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderShippingInfo;

/**
 * 테스트용 주문 픽스처 — 배송지 캡처(#W7-6) 도입 후 {@code Order} 정적 팩토리와 {@code OrderCreateRequest}
 * 가 배송지를 요구하므로, 검증을 통과하는 표준 배송지 스냅샷/요청 DTO 와 그걸 끼운 {@code Order} 생성 헬퍼를 모아 둔다.
 */
public final class OrderFixtures {

    private OrderFixtures() {
    }

    public static OrderShippingInfo sampleShippingInfo() {
        return new OrderShippingInfo("김철수", "01012345678", "서울시 강남구 테헤란로 123", "456호", "06234", false);
    }

    public static ShippingInfoRequest sampleShippingInfoRequest() {
        return new ShippingInfoRequest("김철수", "01012345678", "서울시 강남구 테헤란로 123", "456호", "06234", false);
    }

    public static Order memberOrder(String orderNumber, Long memberId) {
        return Order.placeForMember(orderNumber, memberId, sampleShippingInfo());
    }

    public static Order guestOrder(String orderNumber, String guestEmail, String guestPhone) {
        return Order.placeForGuest(orderNumber, guestEmail, guestPhone, sampleShippingInfo());
    }
}
