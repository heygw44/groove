package com.groove.order.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	boolean existsByOrderMemberIdAndProductIdAndOrderStatus(Long memberId, Long productId, OrderStatus status);

	@Query("select distinct oi.product.id from OrderItem oi "
			+ "where oi.order.member.id = :memberId and oi.order.status in :statuses")
	List<Long> findProductIdsByMemberIdAndOrderStatusIn(@Param("memberId") Long memberId,
			@Param("statuses") Collection<OrderStatus> statuses);
}
