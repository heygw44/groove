package com.groove.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 주문에 이미 접수된 결제 조회 — 주문 레벨 멱등성에 쓰인다(같은 주문에 결제 재요청 시 기존 건 반환,
     * {@code uk_payment_order} UNIQUE 충돌 사전 회피). {@code order_id} 는 UNIQUE 이므로 최대 1건.
     */
    Optional<Payment> findByOrderId(Long orderId);
}
