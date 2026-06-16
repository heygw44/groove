package com.groove.shipping.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShippingRepository extends JpaRepository<Shipping, Long> {

    /**
     * 운송장 번호로 배송 조회 — uk_shipping_tracking UNIQUE 이므로 최대 1건.
     */
    Optional<Shipping> findByTrackingNumber(String trackingNumber);

    /**
     * 주문에 이미 생성된 배송이 있는지 — 결제 완료 이벤트 중복 전달 시 배송이 1건만 생기도록 하는 가드. 최종 방어선은 uk_shipping_order.
     */
    boolean existsByOrderId(Long orderId);

    /**
     * 주문 식별자로 배송을 조회한다 (order_id UNIQUE → 최대 1건).
     */
    Optional<Shipping> findByOrderId(Long orderId);

    /**
     * 식별자로 배송을 조회하되 order 를 함께 로드한다 — 자동 진행이 주문을 락스텝 전진시킬 때 N+1 을 없애려 fetch 한다.
     */
    @EntityGraph(attributePaths = "order")
    Optional<Shipping> findWithOrderById(Long id);

    /**
     * 자동 진행 스케줄러의 PREPARING → SHIPPED 대상 — createdAtBefore 이전에 생성돼 아직 준비 중인 배송.
     * limit 으로 한 주기 처리량을 제한한다. idx_shipping_status (status, created_at) 인덱스가 받친다.
     */
    List<Shipping> findByStatusAndCreatedAtBefore(ShippingStatus status, Instant createdAtBefore, Limit limit);

    /**
     * 자동 진행 스케줄러의 SHIPPED → DELIVERED 대상 — shippedAtBefore 이전에 발송된 배송.
     */
    List<Shipping> findByStatusAndShippedAtBefore(ShippingStatus status, Instant shippedAtBefore, Limit limit);

    /**
     * PII 익명화 배치 대상 — 배송완료(DELIVERED)된 지 보존기간이 지났고 아직 익명화되지 않은 배송을
     * delivered_at 오름차순으로 찾는다. 식별자만 담은 경량 ShippingIdView 프로젝션으로 받고, limit 으로 처리량을 제한한다.
     */
    List<ShippingIdView> findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
            ShippingStatus status, Instant cutoff, Limit limit);

    interface ShippingIdView {
        Long getId();
    }
}
