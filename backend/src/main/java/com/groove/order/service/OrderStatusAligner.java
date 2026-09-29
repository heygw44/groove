package com.groove.order.service;

import org.springframework.stereotype.Component;

import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품주문 일괄 처리(발주확인·발송처리·배송완료) 뒤 Order.status 를 맞추는 임시 동기화. 주문 안 모든 상품주문이
 * 같은 단계에 이르렀을 때만 Order.status 를 올리고, 아니면 그대로 둔다. Order.status 가
 * PENDING/PAID/CANCELED 로 좁아지면 이 클래스는 없앤다.
 */
@Component
@RequiredArgsConstructor
public class OrderStatusAligner {

	private final OrderRepository orderRepository;

	public void alignIfAllItemsMatch(Long orderId, OrderItemStatus targetItemStatus, OrderStatus targetOrderStatus) {
		Order order = orderRepository.findWithItemsById(orderId).orElse(null);
		if (order == null) {
			return;
		}
		boolean allMatch = order.getItems().stream().allMatch(item -> item.getStatus() == targetItemStatus);
		if (allMatch) {
			order.alignStatusWithItems(targetOrderStatus);
		}
	}
}
