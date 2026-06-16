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

    /** 회원 본인 반품 단건 조회 — order.memberId 가 인증 회원과 일치하는 것만, items.orderItem 까지 fetch. */
    @EntityGraph(attributePaths = {"items", "items.orderItem", "order"})
    Optional<Claim> findByIdAndOrder_MemberId(Long id, Long memberId);

    /** 환불 오케스트레이션 전용 — 항목·주문·재입고 대상 앨범까지 한 번에 fetch. */
    @EntityGraph(attributePaths = {"items", "items.orderItem", "items.orderItem.album", "order"})
    Optional<Claim> findWithItemsAndOrderById(Long id);

    /** 한 주문의 거부(REJECTED) 제외 모든 반품 — 접수 시 항목별 잔여 수량 가드용. */
    @EntityGraph(attributePaths = {"items", "items.orderItem"})
    List<Claim> findByOrder_IdAndStatusNot(Long orderId, ClaimStatus status);

    /** 한 주문에서 특정 상태의 반품들 — items 만 fetch. */
    @EntityGraph(attributePaths = "items")
    List<Claim> findByOrder_IdAndStatus(Long orderId, ClaimStatus status);

    /** 자동 진행 스케줄러의 APPROVED → IN_TRANSIT 대상 — approvedAtBefore 이전 승인분(FIFO 정렬). */
    List<Claim> findByStatusAndApprovedAtBeforeOrderByApprovedAtAscIdAsc(
            ClaimStatus status, Instant approvedAtBefore, Limit limit);

    /** 자동 진행 스케줄러의 IN_TRANSIT → INSPECTING 대상 — inTransitAtBefore 이전에 회수 시작된 반품(FIFO 정렬). */
    List<Claim> findByStatusAndInTransitAtBeforeOrderByInTransitAtAscIdAsc(
            ClaimStatus status, Instant inTransitAtBefore, Limit limit);

    /** 자동 진행 스케줄러의 INSPECTING → REFUNDED 대상 — inspectingAtBefore 이전 검수 시작분(FIFO 정렬). */
    List<Claim> findByStatusAndInspectingAtBeforeOrderByInspectingAtAscIdAsc(
            ClaimStatus status, Instant inspectingAtBefore, Limit limit);

    /** 관리자 반품 목록 — 주문번호 노출을 위해 order 를 fetch 한다. */
    @Override
    @EntityGraph(attributePaths = "order")
    Page<Claim> findAll(Pageable pageable);

    /** 관리자 반품 목록(상태 필터). */
    @EntityGraph(attributePaths = "order")
    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);
}
