package com.groove.order.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	boolean existsByOrderMemberIdAndProductIdAndStatusIn(Long memberId, Long productId,
			Collection<OrderItemStatus> statuses);

	@Query("select distinct oi.product.id from OrderItem oi "
			+ "where oi.order.member.id = :memberId and oi.status in :statuses")
	List<Long> findProductIdsByMemberIdAndStatusIn(@Param("memberId") Long memberId,
			@Param("statuses") Collection<OrderItemStatus> statuses);
}
