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
     * 주문에 이미 접수된 결제 조회 — 주문 레벨 멱등성에 쓰인다(같은 주문에 결제 재요청 시 기존 건 반환,
     * {@code uk_payment_order} UNIQUE 충돌 사전 회피). {@code order_id} 는 UNIQUE 이므로 최대 1건.
     */
    Optional<Payment> findByOrderId(Long orderId);

    /**
     * 관리자 환불 처리 전용 (이슈 #69) — {@link #findByOrderId} 와 같지만 {@code SELECT ... FOR UPDATE}
     * (PESSIMISTIC_WRITE) 로 결제 row 를 잠근다. 동시 환불 요청 두 건이 같은 결제에 대해 상태 확인 → PG
     * {@code refund()} 호출까지 동시에 통과해 PG 가 이중 환불을 받는 race 를 차단한다.
     *
     * <p>락은 트랜잭션 종료 시 해제되며 {@code order_id} UNIQUE + {@code uk_payment_order} 가 잡고 있어
     * 정확히 한 row 만 잠그므로 데드락 가능성은 없다. 이중 안전선으로 PG 측 멱등 키는 별도 이슈(#72)에서 도입.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /**
     * 웹훅 수신·폴링 동기화 시 PG 거래 식별자로 결제를 조회한다 — 보상 트랜잭션(재고 복원)을 위해
     * {@code order} + {@code order.items} + {@code order.items.album} 까지 한 번에 fetch 한다
     * ({@code OrderService.cancel} 의 {@code findWithAlbumsByOrderNumber} 와 같은 이유). 결제마다 새 거래
     * 식별자를 발급하므로({@code request()}) 최대 1건이며, {@code idx_payment_pg_tx} 인덱스가 이 조회를 받친다.
     */
    @EntityGraph(attributePaths = {"order", "order.items", "order.items.album"})
    Optional<Payment> findWithOrderAndItemsByPgTransactionId(String pgTransactionId);

    /**
     * 폴링 스케줄러 대상 — {@code createdAtBefore} 이전에 접수돼 아직 PENDING 으로 머문 결제. 갓 접수된 결제는
     * 정상 웹훅이 곧 도착하므로 제외한다. {@code limit} 으로 한 주기 처리량을 제한해(메모리 바운드) 적체 시에도
     * 다음 주기에 나머지를 처리한다. {@code idx_payment_status_created} (status, created_at) 인덱스가 이 액세스
     * 패턴을 받치며, 인덱스 스캔 순서상 결과는 대체로 {@code created_at} 오름차순(오래된 것 우선)이다 (V11).
     */
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant createdAtBefore, Limit limit);
}
