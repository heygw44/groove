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

/** Specification 은 관리자 주문 목록의 동적 필터 전용. */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    /** items + items.album 까지 한 번에 fetch. */
    @EntityGraph(attributePaths = {"items", "items.album"})
    Optional<Order> findWithAlbumsByOrderNumber(String orderNumber);

    /** 반품/부분취소/본인취소의 동시 전이를 직렬화. items 는 호출 측이 지연 로드. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    /** 배송 자동진행 직렬화용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    boolean existsByOrderNumber(String orderNumber);

    /** items 는 @BatchSize IN 쿼리로 일괄 로드. */
    Page<Order> findByMemberId(Long memberId, Pageable pageable);

    Page<Order> findByMemberIdAndStatus(Long memberId, OrderStatus status, Pageable pageable);

    long countByMemberId(Long memberId);

    boolean existsByMemberIdAndStatusIn(Long memberId, Collection<OrderStatus> statuses);

    /** 배송 reconciliation 안전망 — PAID 됐는데 배송 없는 주문. 경량 프로젝션 + limit 으로 주기 처리량 제한. */
    List<OrderNumberView> findByStatusAndPaidAtBeforeOrderByPaidAtAsc(OrderStatus status, Instant cutoff, Limit limit);

    /** 비배송 종착 주문 PII 익명화 배치 대상 (보존기간 경과 + 미익명화). */
    List<OrderNumberView> findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<OrderStatus> statuses, Instant cutoff, Limit limit);

    /** 앨범 삭제 차단 검사. */
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM OrderItem oi WHERE oi.album.id = :albumId")
    boolean existsByAlbumId(@Param("albumId") Long albumId);

    List<OrderNumberView> findByIdIn(Collection<Long> ids);

    interface OrderNumberView {
        Long getId();

        String getOrderNumber();
    }
}
