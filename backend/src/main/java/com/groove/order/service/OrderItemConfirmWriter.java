package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/** 구매자 구매확정(SHIPPING·DELIVERED → PURCHASE_CONFIRMED) 쓰기 전용. */
@Service
@RequiredArgsConstructor
public class OrderItemConfirmWriter {

	private final OrderRepository orderRepository;
	private final Clock clock;

	@Transactional
	public void confirm(Long memberId, Long orderId, Long itemId) {
		Order locked = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (!locked.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderItem item = order.getItems().stream()
				.filter(candidate -> candidate.getId().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (!item.confirmPurchase(LocalDateTime.now(clock))) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
	}
}
