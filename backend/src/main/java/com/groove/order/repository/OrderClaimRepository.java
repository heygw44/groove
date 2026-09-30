package com.groove.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;

public interface OrderClaimRepository extends JpaRepository<OrderClaim, Long> {

	boolean existsByOrderItemIdAndStatusIn(Long orderItemId, Collection<OrderClaimStatus> statuses);

	@Query("select c from OrderClaim c where c.id = :id and c.orderItem.order.member.id = :memberId")
	Optional<OrderClaim> findByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);

	@Query("select c from OrderClaim c where c.orderItem.id in :orderItemIds and c.status = :status")
	List<OrderClaim> findAllByOrderItemIdInAndStatus(@Param("orderItemIds") Collection<Long> orderItemIds,
			@Param("status") OrderClaimStatus status);

	/** 철회 가능한(REQUESTED) 클레임 id 를 상품주문 id 별로 한 번의 쿼리로 모은다. 없는 상품주문은 키에 없다. */
	default Map<Long, Long> findRequestedClaimIdsByOrderItemId(Collection<Long> orderItemIds) {
		if (orderItemIds.isEmpty()) {
			return Map.of();
		}
		return findAllByOrderItemIdInAndStatus(orderItemIds, OrderClaimStatus.REQUESTED).stream()
				.collect(Collectors.toMap(claim -> claim.getOrderItem().getId(), OrderClaim::getId, (a, b) -> a));
	}

	/**
	 * 상품주문·주문을 함께 가져온다. 트랜잭션이 끝난 뒤(다른 트랜잭션에 걸친 승인·판매취소 흐름)에도 호출자가
	 * claim.getOrderItem().getOrder() 를 안전하게 읽을 수 있어야 하기 때문이다 - 지연 로딩인 채로 두면
	 * LazyInitializationException 이 난다.
	 */
	@EntityGraph(attributePaths = {"orderItem", "orderItem.order"})
	Optional<OrderClaim> findWithOrderItemById(Long id);

	/** 정렬은 호출자가 넘기는 {@code pageable}(요청일 최신순)을 그대로 쓴다 - JPQL 에 order by 를 같이 두면 중복된다. */
	@Query("""
			select c from OrderClaim c
			join fetch c.orderItem oi
			join fetch oi.order o
			join fetch o.member m
			join fetch oi.product p
			where (:type is null or c.type = :type)
			and (:status is null or c.status = :status)
			""")
	Page<OrderClaim> search(@Param("type") OrderClaimType type, @Param("status") OrderClaimStatus status,
			Pageable pageable);

	/** 유형·상태별 건수를 한 번에 센다. 건이 없는 조합은 행이 없다. */
	@Query("select c.type as type, c.status as status, count(c) as count from OrderClaim c "
			+ "group by c.type, c.status")
	List<OrderClaimCountRow> countByTypeAndStatus();
}
