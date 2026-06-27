package com.groove.order.application;

import com.groove.coupon.application.OrderNumberLookup;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderRepository.OrderNumberView;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 쿠폰 보유 목록의 주문번호 표시를 위한 {@link OrderNumberLookup} 구현(coupon→order 역참조 차단, #349).
 * 빈 orderId 집합이거나 null orderId 조회를 위해 null 키를 허용하는 맵을 돌려준다.
 */
@Component
public class CouponOrderNumberLookup implements OrderNumberLookup {

    private final OrderRepository orderRepository;

    public CouponOrderNumberLookup(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Map<Long, String> orderNumbersByIds(Set<Long> orderIds) {
        // Collections.emptyMap()(Map.of() 아님): 호출 측이 ISSUED 쿠폰의 null orderId 로 get(null) 을 호출한다.
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRepository.findByIdIn(orderIds).stream()
                .collect(Collectors.toMap(OrderNumberView::getId, OrderNumberView::getOrderNumber));
    }
}
