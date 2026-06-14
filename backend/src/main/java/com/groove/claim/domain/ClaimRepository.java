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

    /**
     * 반품 상태 변경(승인/거부/회수/검수/환불) 직렬화용 — 반품 행을 {@code PESSIMISTIC_WRITE} 로 잠근다 (#239).
     * 락 이후 읽는 상태는 최신 커밋본이라, 동시 {@code completeRefund}/{@code reject} 가 stale 스냅샷으로 서로의
     * 전이를 덮어쓰는 race(거부된 반품이 환불로 되살아나거나 이중 정산)를 막는다. 항목 그래프는 호출 측이 트랜잭션
     * 안에서 지연 로드한다(반품 항목 수가 적어 N+1 미미) — {@code FOR UPDATE} 와 컬렉션 fetch 조합을 피한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> findByIdForUpdate(@Param("id") Long id);

    /**
     * 회원 본인 반품 단건 조회 ({@code GET /claims/{id}}) — {@code order.memberId} 가 인증 회원과 일치하는 것만
     * 돌려준다. 타인/게스트 반품은 빈 결과 → 호출 측이 {@code ClaimNotFoundException}(404) 로 통일해 존재 노출을 막는다.
     * 응답이 항목을 노출하므로 {@code items.orderItem} 까지 fetch 한다.
     */
    @EntityGraph(attributePaths = {"items", "items.orderItem", "order"})
    Optional<Claim> findByIdAndOrder_MemberId(Long id, Long memberId);

    /**
     * 환불 오케스트레이션({@code ClaimService.completeRefund}) 전용 — 항목·주문·재입고 대상 앨범까지 한 번에 fetch.
     * claimItem.getOrderItem().getAlbum().adjustStock() 가 LAZY 추가 SELECT(N+1) 없이 동작하도록 그래프를 미리 펼친다.
     */
    @EntityGraph(attributePaths = {"items", "items.orderItem", "items.orderItem.album", "order"})
    Optional<Claim> findWithItemsAndOrderById(Long id);

    /**
     * 한 주문의 종착 거부(REJECTED)를 제외한 모든 반품 — 접수 시 항목별 잔여 수량 가드용 (#239).
     * 거부된 반품은 상품이 실제로 반품되지 않았으므로 잔여 수량 회계에서 제외한다(상태 != REJECTED 만 합산).
     */
    @EntityGraph(attributePaths = {"items", "items.orderItem"})
    List<Claim> findByOrder_IdAndStatusNot(Long orderId, ClaimStatus status);

    /**
     * 한 주문에서 특정 상태의 반품들 — 환불액 비례 배분 시 이미 REFUNDED 된 반품들의 정가 합(분자 누적) 산정용.
     * {@code getGross()} 는 ClaimItem 자체 컬럼만 쓰므로 {@code items} 만 fetch 한다.
     */
    @EntityGraph(attributePaths = "items")
    List<Claim> findByOrder_IdAndStatus(Long orderId, ClaimStatus status);

    /**
     * 자동 진행 스케줄러의 APPROVED → IN_TRANSIT 대상 — {@code approvedAtBefore} 이전에 승인돼 회수 대기 중인 반품.
     * 스케줄러는 식별자만 쓰므로 {@code items} 는 로드하지 않는다. {@code idx_claim_status} 가 받친다. 오래 기다린
     * 건부터 공정하게 소진되도록 단계 시각 오름차순 + {@code id} tie-breaker 로 정렬한다(일부 건 반복 실패 시 적체 방지).
     */
    List<Claim> findByStatusAndApprovedAtBeforeOrderByApprovedAtAscIdAsc(
            ClaimStatus status, Instant approvedAtBefore, Limit limit);

    /** 자동 진행 스케줄러의 IN_TRANSIT → INSPECTING 대상 — {@code inTransitAtBefore} 이전에 회수 시작된 반품(FIFO 정렬). */
    List<Claim> findByStatusAndInTransitAtBeforeOrderByInTransitAtAscIdAsc(
            ClaimStatus status, Instant inTransitAtBefore, Limit limit);

    /** 자동 진행 스케줄러의 INSPECTING → REFUNDED(검수 자동통과+환불) 대상 — {@code inspectingAtBefore} 이전 검수 시작분(FIFO 정렬). */
    List<Claim> findByStatusAndInspectingAtBeforeOrderByInspectingAtAscIdAsc(
            ClaimStatus status, Instant inspectingAtBefore, Limit limit);

    /** 관리자 반품 목록 — 주문번호 노출을 위해 {@code order} 를 fetch 한다(ManyToOne, 인메모리 페이지네이션 무관). */
    @Override
    @EntityGraph(attributePaths = "order")
    Page<Claim> findAll(Pageable pageable);

    /** 관리자 반품 목록(상태 필터). */
    @EntityGraph(attributePaths = "order")
    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);
}
