package com.groove.order.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.cart.repository.CartItemRepository;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderSource;

import lombok.RequiredArgsConstructor;

/**
 * 결제 승인·가상계좌 발급 시점의 주문 확정. 호출자(PaymentConfirmWriter)의 트랜잭션에 반드시 합류해야 하므로
 * MANDATORY 로 건다 - 별도 트랜잭션으로 뜨면 주문 확정과 결제 승인이 따로 커밋돼 정합성이 깨진다.
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

	private final CartItemRepository cartItemRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public void place(Order order, LocalDateTime at) {
		order.place(at);
		if (order.getOrderSource() == OrderSource.CART) {
			List<Long> productIds = order.getItems().stream()
					.map(item -> item.getProduct().getId())
					.distinct()
					.toList();
			cartItemRepository.deleteByCartMemberIdAndProductIdIn(order.getMember().getId(), productIds);
		}
	}
}
