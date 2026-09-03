package com.groove.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByOrderNumber(String orderNumber);

	Optional<Order> findByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = {"items", "items.product"})
	Optional<Order> findWithItemsById(Long id);

	@EntityGraph(attributePaths = {"items", "items.product"})
	Optional<Order> findWithItemsByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = {"items", "items.product", "member"})
	Optional<Order> findWithItemsAndMemberById(Long id);
}
