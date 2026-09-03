package com.groove.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	boolean existsByOrderMemberIdAndProductIdAndOrderStatus(Long memberId, Long productId, OrderStatus status);
}
