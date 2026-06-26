package com.groove.payment.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 주문에 접수된 결제 조회 — order_id 는 UNIQUE 이므로 최대 1건. */
    Optional<Payment> findByOrderId(Long orderId);

    /** findByOrderId + FOR UPDATE(PESSIMISTIC_WRITE) 로 결제 row 잠금. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /** PG 거래 식별자로 가벼운 선조회(연관 fetch 없음). 웹훅이 outbound 재조회 전 로컬 존재·상태를 먼저 확인 — 미존재/종착은 재조회 없이 무해 처리. */
    Optional<Payment> findByPgTransactionId(String pgTransactionId);

    /** PG 거래 식별자로 결제 + order/items/items.album 까지 한 번에 fetch. */
    @EntityGraph(attributePaths = {"order", "order.items", "order.items.album"})
    Optional<Payment> findWithOrderAndItemsByPgTransactionId(String pgTransactionId);

    /**
     * PG 거래 식별자로 FOR UPDATE(PESSIMISTIC_WRITE) 잠금 — 콜백(웹훅/폴링) 동시 적용을 환불 경로(findByOrderIdForUpdate)와 대칭 직렬화.
     * order/items 는 fetch join 하지 않는다 — album 행까지 잠그면 재고 차감/복원과 락 경합이 생기므로 같은 트랜잭션에서 lazy 로딩.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.pgTransactionId = :pgTransactionId")
    Optional<Payment> findByPgTransactionIdForUpdate(@Param("pgTransactionId") String pgTransactionId);

    /** createdAtBefore 이전 접수돼 해당 status 로 머문 결제를 limit 만큼 조회(대체로 created_at 오름차순). */
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant createdAtBefore, Limit limit);
}
