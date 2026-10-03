package com.groove.order.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.order.dto.OrderItemConfirmCandidate;
import com.groove.order.dto.OrderItemDeliverCandidate;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	boolean existsByOrderMemberIdAndProductIdAndStatusIn(Long memberId, Long productId,
			Collection<OrderItemStatus> statuses);

	// claimStatus null 은 클레임 없음이라 제외하면 안 된다. 파생 쿼리 NotIn 은 NULL 행을 떨어뜨리므로 직접 쓴다.
	@Query("select case when count(oi) > 0 then true else false end from OrderItem oi "
			+ "where oi.order.member.id = :memberId and oi.product.id = :productId and oi.status in :statuses "
			+ "and (oi.claimStatus is null or oi.claimStatus not in :excludedClaimStatuses)")
	boolean existsByMemberIdAndProductIdAndStatusInExcludingClaims(@Param("memberId") Long memberId,
			@Param("productId") Long productId, @Param("statuses") Collection<OrderItemStatus> statuses,
			@Param("excludedClaimStatuses") Collection<OrderItemClaimStatus> excludedClaimStatuses);

	/** 취소·반품 클레임 처리 직후 응답을 만들 때 쓴다. product 를 함께 가져와 추가 지연로딩이 없게 한다. */
	@EntityGraph(attributePaths = {"product"})
	Optional<OrderItem> findWithProductById(Long id);

	@Query("select distinct oi.product.id from OrderItem oi "
			+ "where oi.order.member.id = :memberId and oi.status in :statuses")
	List<Long> findProductIdsByMemberIdAndStatusIn(@Param("memberId") Long memberId,
			@Param("statuses") Collection<OrderItemStatus> statuses);

	// 관리자 일괄 처리의 잠금 순서를 정하기 위한 조회다. 엔티티를 로드하면 이후 주문 락 획득 전에 스냅샷이 영속성
	// 컨텍스트에 박혀 락을 기다리는 동안의 최신 상태를 놓치므로 스칼라 id 만 뽑는다.
	@Query("select distinct oi.order.id from OrderItem oi where oi.id in :ids")
	List<Long> findDistinctOrderIdsByIdIn(@Param("ids") Collection<Long> ids);

	@Query("select oi.order.id from OrderItem oi where oi.id = :id")
	Optional<Long> findOrderIdById(@Param("id") Long id);

	// 처리 못 한 행이 앞에 남아도 뒤 후보로 넘어가도록 (shippedAt, id) keyset 커서 뒤만 조회한다.
	@Query("select new com.groove.order.dto.OrderItemDeliverCandidate(oi.id, oi.shippedAt) from OrderItem oi "
			+ "where oi.status = :status and oi.shippedAt <= :cutoff "
			+ "and (oi.shippedAt > :afterShippedAt "
			+ "or (oi.shippedAt = :afterShippedAt and oi.id > :afterId)) "
			+ "order by oi.shippedAt asc, oi.id asc")
	List<OrderItemDeliverCandidate> findDeliverCandidates(@Param("status") OrderItemStatus status,
			@Param("cutoff") LocalDateTime cutoff, @Param("afterShippedAt") LocalDateTime afterShippedAt,
			@Param("afterId") Long afterId, Limit limit);

	// 처리 못 한 행이 앞에 남아도 뒤 후보로 넘어가도록 (deliveredAt, id) keyset 커서 뒤만 조회한다.
	@Query("select new com.groove.order.dto.OrderItemConfirmCandidate(oi.id, oi.deliveredAt) from OrderItem oi "
			+ "where oi.status = :status and oi.deliveredAt <= :cutoff "
			+ "and (oi.claimStatus is null or oi.claimStatus not in :inProgressClaimStatuses) "
			+ "and (oi.deliveredAt > :afterDeliveredAt "
			+ "or (oi.deliveredAt = :afterDeliveredAt and oi.id > :afterId)) "
			+ "order by oi.deliveredAt asc, oi.id asc")
	List<OrderItemConfirmCandidate> findConfirmCandidates(@Param("status") OrderItemStatus status,
			@Param("cutoff") LocalDateTime cutoff,
			@Param("inProgressClaimStatuses") Collection<OrderItemClaimStatus> inProgressClaimStatuses,
			@Param("afterDeliveredAt") LocalDateTime afterDeliveredAt, @Param("afterId") Long afterId,
			Limit limit);
}
