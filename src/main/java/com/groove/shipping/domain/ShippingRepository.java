package com.groove.shipping.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShippingRepository extends JpaRepository<Shipping, Long> {

    /**
     * 운송장 번호로 배송 조회 ({@code GET /shippings/{trackingNumber}}) — {@code uk_shipping_tracking} UNIQUE
     * 이므로 최대 1건. 응답 DTO 는 {@code order} 를 참조하지 않으므로 join 은 두지 않는다.
     */
    Optional<Shipping> findByTrackingNumber(String trackingNumber);

    /**
     * 주문에 이미 생성된 배송이 있는지 — 결제 완료 이벤트가 중복 전달돼도 배송이 1건만 생기도록 하는 가드.
     * {@code order_id} 는 UNIQUE 이므로 최종 방어선은 {@code uk_shipping_order} 이지만, 흔한 순차 재전달은 이 조회로 미리 거른다.
     */
    boolean existsByOrderId(Long orderId);

    /**
     * 자동 진행 스케줄러의 PREPARING → SHIPPED 대상 — {@code createdAtBefore} 이전에 생성돼 아직 준비 중인 배송.
     * {@code limit} 으로 한 주기 처리량을 제한해(메모리 바운드) 적체 시에도 다음 주기에 나머지를 처리한다.
     * {@code idx_shipping_status} (status, created_at) 인덱스가 이 액세스 패턴을 받친다.
     */
    List<Shipping> findByStatusAndCreatedAtBefore(ShippingStatus status, Instant createdAtBefore, Limit limit);

    /**
     * 자동 진행 스케줄러의 SHIPPED → DELIVERED 대상 — {@code shippedAtBefore} 이전에 발송된 배송. 대상 집합이
     * 작은 과도 상태이므로 {@code idx_shipping_status} 의 status 프리픽스 스캔 + {@code shipped_at} 인메모리 필터로 충분하다.
     */
    List<Shipping> findByStatusAndShippedAtBefore(ShippingStatus status, Instant shippedAtBefore, Limit limit);
}
