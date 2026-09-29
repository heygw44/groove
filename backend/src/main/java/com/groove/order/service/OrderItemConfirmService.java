package com.groove.order.service;

import org.springframework.stereotype.Service;

import com.groove.order.dto.OrderDetailResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderItemConfirmService {

	private final OrderItemConfirmWriter writer;
	private final OrderService orderService;

	public OrderDetailResponse confirm(Long memberId, Long orderId, Long itemId) {
		writer.confirm(memberId, orderId, itemId);
		return orderService.getDetailAfterAction(memberId, orderId);
	}
}
