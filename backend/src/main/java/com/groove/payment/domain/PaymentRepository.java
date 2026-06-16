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

    /**
     * 주문에 접수된 결제 조회 — order_id 는 UNIQUE 이므로 최대 1건.
     */
    Optional<Payment> findByOrderId(Long orderId);

    /**
     * findByOrderId 와 같되 SELECT ... FOR UPDATE(PESSIMISTIC_WRITE)로 결제 row 를 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /**
     * PG 거래 식별자로 결제를 조회하며 order + order.items + order.items.album 까지 한 번에 fetch 한다.
     */
    @EntityGraph(attributePaths = {"order", "order.items", "order.items.album"})
    Optional<Payment> findWithOrderAndItemsByPgTransactionId(String pgTransactionId);

    /**
     * createdAtBefore 이전에 접수돼 주어진 status 로 머문 결제를 limit 만큼 조회한다.
     * 결과는 대체로 created_at 오름차순.
     */
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant createdAtBefore, Limit limit);
}
