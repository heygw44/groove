package com.groove.claim.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /** 반품 상태 변경 직렬화용 — 반품 행을 PESSIMISTIC_WRITE 로 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> findByIdForUpdate(@Param("id") Long id);

    /** order.memberId 가 인증 회원과 일치하는 것만. */
    @EntityGraph(attributePaths = {"items", "items.orderItem", "order"})
    Optional<Claim> findByIdAndOrder_MemberId(Long id, Long memberId);

    /** 환불 오케스트레이션 전용 — 재입고 대상 앨범까지 fetch. */
    @EntityGraph(attributePaths = {"items", "items.orderItem", "items.orderItem.album", "order"})
    Optional<Claim> findWithItemsAndOrderById(Long id);

    /** 접수 시 항목별 잔여 수량 가드용(거부 제외). */
    @EntityGraph(attributePaths = {"items", "items.orderItem"})
    List<Claim> findByOrder_IdAndStatusNot(Long orderId, ClaimStatus status);

    @EntityGraph(attributePaths = "items")
    List<Claim> findByOrder_IdAndStatus(Long orderId, ClaimStatus status);

    /** 자동 진행 스케줄러 대상 (FIFO 정렬). */
    List<Claim> findByStatusAndApprovedAtBeforeOrderByApprovedAtAscIdAsc(
            ClaimStatus status, Instant approvedAtBefore, Limit limit);

    List<Claim> findByStatusAndInTransitAtBeforeOrderByInTransitAtAscIdAsc(
            ClaimStatus status, Instant inTransitAtBefore, Limit limit);

    List<Claim> findByStatusAndInspectingAtBeforeOrderByInspectingAtAscIdAsc(
            ClaimStatus status, Instant inspectingAtBefore, Limit limit);

    /** order 를 fetch 해 주문번호 노출. */
    @Override
    @EntityGraph(attributePaths = "order")
    Page<Claim> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "order")
    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);
}
