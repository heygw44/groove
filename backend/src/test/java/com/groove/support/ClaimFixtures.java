package com.groove.support;

import com.groove.catalog.album.domain.Album;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;

import java.time.Instant;

/**
 * 반품(claim) 통합 테스트 픽스처 — 반품 접수 자격(DELIVERED + delivered_at)을 갖춘 주문을 영속화한다.
 */
public final class ClaimFixtures {

    private ClaimFixtures() {
    }

    /**
     * 배송완료(DELIVERED) 회원 주문 + PAID 결제 + delivered_at 배송행을 저장하고 주문을 반환한다.
     * {@code uniq} 는 orderNumber·PG 거래키·송장번호의 충돌 방지 토큰(예: {@code seq + "-" + System.nanoTime()}).
     */
    public static Order persistDeliveredOrder(OrderRepository orderRepository,
                                              PaymentRepository paymentRepository,
                                              ShippingRepository shippingRepository,
                                              Album album, Long memberId, int qty, String uniq) {
        Order order = OrderFixtures.memberOrder("ORD-CLM-" + uniq, memberId);
        order.addItem(OrderItem.create(album, qty));
        order.changeStatus(OrderStatus.PAID, null, Instant.now());
        order.changeStatus(OrderStatus.PREPARING, null, Instant.now());
        order.changeStatus(OrderStatus.SHIPPED, null, Instant.now());
        order.changeStatus(OrderStatus.DELIVERED, null, Instant.now());
        Order saved = orderRepository.saveAndFlush(order);

        Payment payment = Payment.initiate(saved, saved.getPayableAmount(), PaymentMethod.CARD, "MOCK",
                "mock-tx-" + uniq);
        payment.markPaid(Instant.now());
        paymentRepository.saveAndFlush(payment);

        Shipping shipping = Shipping.prepare(saved, saved.getShippingInfo(), "trk-" + uniq);
        shipping.markShipped(Instant.now());
        shipping.markDelivered(Instant.now());
        shippingRepository.saveAndFlush(shipping);
        return saved;
    }
}
