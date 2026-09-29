package com.groove.order.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품주문 한 건을 자동 구매확정(DELIVERED → PURCHASE_CONFIRMED) 처리한다. Order.status 에는
 * PURCHASE_CONFIRMED 에 대응하는 값이 없어 {@link OrderStatusAligner} 는 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderPurchaseConfirmService {

	private final OrderItemRepository orderItemRepository;
	private final OrderRepository orderRepository;

	@Transactional
	public boolean confirm(Long itemId, LocalDateTime cutoff, LocalDateTime now) {
		Long orderId = orderItemRepository.findOrderIdById(itemId).orElse(null);
		if (orderId == null) {
			return false;
		}
		orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderItem item = orderItemRepository.findById(itemId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (item.getStatus() != OrderItemStatus.DELIVERED || item.getDeliveredAt() == null
				|| item.getDeliveredAt().isAfter(cutoff) || OrderItemClaimStatus.isInProgress(item.getClaimStatus())) {
			return false;
		}
		return item.confirmPurchase(now);
	}
}
