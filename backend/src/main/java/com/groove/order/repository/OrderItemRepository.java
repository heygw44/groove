package com.groove.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	boolean existsByOrderMemberIdAndProductIdAndStatusIn(Long memberId, Long productId,
			Collection<OrderItemStatus> statuses);

	/** 취소·반품 클레임 처리 직후 응답을 만들 때 쓴다. product 를 함께 가져와 추가 지연로딩이 없게 한다. */
	@EntityGraph(attributePaths = {"product"})
	Optional<OrderItem> findWithProductById(Long id);

	@Query("select distinct oi.product.id from OrderItem oi "
			+ "where oi.order.member.id = :memberId and oi.status in :statuses")
	List<Long> findProductIdsByMemberIdAndStatusIn(@Param("memberId") Long memberId,
			@Param("statuses") Collection<OrderItemStatus> statuses);
}
