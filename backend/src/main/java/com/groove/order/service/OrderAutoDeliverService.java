package com.groove.order.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품주문 한 건을 자동 배송완료(SHIPPING → DELIVERED) 처리한다. 후보 조회와 잠금 사이 상태가 바뀔 수 있어(관리자가
 * 먼저 처리하는 등) 잠금 뒤 조건을 다시 확인한다.
 */
@Service
@RequiredArgsConstructor
public class OrderAutoDeliverService {

	private final OrderItemRepository orderItemRepository;
	private final OrderRepository orderRepository;
	private final OrderStatusAligner orderStatusAligner;

	@Transactional
	public boolean deliver(Long itemId, LocalDateTime cutoff, LocalDateTime now) {
		Long orderId = orderItemRepository.findOrderIdById(itemId).orElse(null);
		if (orderId == null) {
			return false;
		}
		orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderItem item = orderItemRepository.findById(itemId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (item.getStatus() != OrderItemStatus.SHIPPING || item.getShippedAt() == null
				|| item.getShippedAt().isAfter(cutoff)) {
			return false;
		}
		if (!item.completeDelivery(now)) {
			return false;
		}
		orderStatusAligner.alignIfAllItemsMatch(orderId, OrderItemStatus.DELIVERED, OrderStatus.DELIVERED);
		return true;
	}
}
