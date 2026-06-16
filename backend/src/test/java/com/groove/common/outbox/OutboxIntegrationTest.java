package com.groove.common.outbox;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.application.PaymentCallbackService;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트랜잭셔널 아웃박스 E2E — Testcontainers MySQL 위에서 실제 트랜잭션/릴레이를 검증한다.
 *
 * <ul>
 *   <li>원자 기록: PaymentCallbackService.applyResult 가 PAID 와 같은 트랜잭션에서 ORDER_PAID 행을 남긴다(릴레이 전 배송 없음).</li>
 *   <li>at-least-once 발행: 릴레이가 미발행 행을 컨슈머에 디스패치해 배송을 생성하고 발행 완료로 표시한다.</li>
 *   <li>정확히 1회: 발행 완료 표시 직전 크래시(재전달/재기동)를 재현해도 멱등 컨슈머로 배송은 1건이다.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("아웃박스 E2E — 원자 기록 / at-least-once 릴레이 / 멱등 컨슈머 (#237)")
class OutboxIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ShippingRepository shippingRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private PaymentCallbackService paymentCallbackService;
    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        shippingRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    private record Payable(String pgTx, Long orderId) {
    }

    // PENDING 게스트 주문 + PENDING 결제를 영속화하고 PG 거래 식별자/주문 id 를 돌려준다
    private Payable persistPayableOrder() {
        String pgTx = "mock-tx-" + UUID.randomUUID().toString().substring(0, 12);
        Order order = orderRepository.saveAndFlush(
                OrderFixtures.guestOrder("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "guest@example.com", "01099998888"));
        paymentRepository.saveAndFlush(Payment.initiate(order, 35_000L, PaymentMethod.MOCK, "MOCK", pgTx));
        return new Payable(pgTx, order.getId());
    }

    private OrderStatus orderStatusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("applyResult: PAID 와 같은 트랜잭션에서 아웃박스에 기록되고, 릴레이 전에는 배송이 없다")
    void applyResult_writesOutboxAtomically_relayThenCreatesShipping() {
        Payable p = persistPayableOrder();

        paymentCallbackService.applyResult(p.pgTx(), PaymentStatus.PAID, null);

        // 미발행 ORDER_PAID 행 1건, 배송은 미생성(릴레이 전)
        assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(10)))
                .singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("ORDER_PAID"));
        assertThat(shippingRepository.findAll()).isEmpty();
        assertThat(orderStatusOf(p.orderId())).isEqualTo(OrderStatus.PAID);

        // 릴레이 → 배송 1건 생성 + 주문 PREPARING + 아웃박스 발행 완료(미발행 0건)
        outboxRelayScheduler.relayPendingEvents();

        assertThat(shippingRepository.findAll()).hasSize(1);
        assertThat(orderStatusOf(p.orderId())).isEqualTo(OrderStatus.PREPARING);
        assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(10))).isEmpty();
    }

    @Test
    @DisplayName("재전달/프로세스 재기동(발행 완료 표시 직전 크래시)에도 배송은 정확히 1회 — 멱등 컨슈머")
    void redelivery_exactlyOnceShipping() {
        Payable p = persistPayableOrder();
        paymentCallbackService.applyResult(p.pgTx(), PaymentStatus.PAID, null);
        outboxRelayScheduler.relayPendingEvents();
        assertThat(shippingRepository.findAll()).hasSize(1);

        // 발행 완료 표시 직전 크래시 재현 — 같은 행을 미발행으로 되돌려 재전달시킨다
        OutboxEvent row = outboxEventRepository.findAll().get(0);
        ReflectionTestUtils.setField(row, "publishedAt", null);
        outboxEventRepository.saveAndFlush(row);

        outboxRelayScheduler.relayPendingEvents();

        // 멱등으로 배송은 1건, 행은 다시 발행 완료로 표시된다
        assertThat(shippingRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(10))).isEmpty();
    }
}
