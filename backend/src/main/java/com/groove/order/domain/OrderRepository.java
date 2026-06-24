package com.groove.order.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JpaSpecificationExecutor 확장 — 관리자 주문 목록의 동적 필터(상태/회원/기간)를
 * OrderSpecifications 조각으로 표현한다. Specification 은 admin 경로 전용.
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /** 단건 조회 (회원 GET / 게스트 lookup) — items 까지 fetch 한다. */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    /** 취소 흐름 전용 — items + items.album 까지 한 번에 fetch 한다. */
    @EntityGraph(attributePaths = {"items", "items.album"})
    Optional<Order> findWithAlbumsByOrderNumber(String orderNumber);

    /**
     * 주문 행을 orderNumber 로 PESSIMISTIC_WRITE 잠근다 — 반품 접수/부분취소(ClaimService)와 회원 본인 취소(OrderService.cancel)의
     * 동시 전이를 직렬화한다. items 는 호출 측이 지연 로드.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    /** 배송 자동진행 직렬화용 — orderId 로 주문 행을 PESSIMISTIC_WRITE 로 잠근다. items 는 호출 측이 지연 로드. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrderNumber(String orderNumber);

    /** 회원 주문 목록 (페이징). items 는 @BatchSize IN 쿼리로 일괄 로드된다. */
    Page<Order> findByMemberId(Long memberId, Pageable pageable);

    Page<Order> findByMemberIdAndStatus(Long memberId, OrderStatus status, Pageable pageable);

    /** 회원 본인 주문 수 — member 스코프 카운트. */
    long countByMemberId(Long memberId);

    /** 회원 탈퇴 차단 검사 — 주어진 상태 집합에 해당하는 회원 주문의 존재 여부. */
    boolean existsByMemberIdAndStatusIn(Long memberId, Collection<OrderStatus> statuses);

    /**
     * 배송 reconciliation 안전망 전용 — PAID 된 지 일정 시간 지났는데 배송이 없는 주문을 paid_at 오름차순으로 찾는다.
     * 경량 OrderNumberView 프로젝션으로 받고, limit 으로 한 주기 처리량을 제한한다.
     */
    List<OrderNumberView> findByStatusAndPaidAtBeforeOrderByPaidAtAsc(OrderStatus status, Instant cutoff, Limit limit);

    /**
     * 비배송 종착 주문 PII 익명화 배치 대상 — 주어진 상태 집합 중 updated_at 후 보존기간이 지났고
     * anonymized_at IS NULL 인 건을 updated_at 오름차순으로 찾는다. 경량 OrderNumberView 프로젝션, limit 으로 처리량 제한.
     */
    List<OrderNumberView> findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<OrderStatus> statuses, Instant cutoff, Limit limit);

    /** 앨범 삭제 차단 검사 — 해당 album 을 참조하는 order_item 존재 여부. */
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM OrderItem oi WHERE oi.album.id = :albumId")
    boolean existsByAlbumId(@Param("albumId") Long albumId);

    /** id 집합으로 (id, orderNumber) 두 컬럼만 IN 조회하는 경량 프로젝션. */
    List<OrderNumberView> findByIdIn(Collection<Long> ids);

    interface OrderNumberView {
        Long getId();

        String getOrderNumber();
    }
}
