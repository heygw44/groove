package com.groove.order.service;

import org.springframework.stereotype.Service;

import com.groove.order.dto.OrderDetailResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderShippingAddressService {

	private final OrderShippingAddressWriter writer;
	private final OrderService orderService;

	public OrderDetailResponse changeShippingAddress(Long memberId, Long orderId, Long addressId) {
		writer.changeAddress(memberId, orderId, addressId);
		return orderService.getDetailAfterAction(memberId, orderId);
	}
}
