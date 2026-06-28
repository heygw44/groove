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
 * 트랜잭셔널 아웃박스 E2E (Testcontainers MySQL). 원자 기록(applyResult 가 PAID 와 같은 트랜잭션에서
 * ORDER_PAID 행 기록), at-least-once 발행, 발행 표시 직전 크래시에도 멱등 컨슈머로 배송 정확히 1회를 다룬다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("아웃박스 E2E — 원자 기록 / at-least-once 릴레이 / 멱등 컨슈머 (#237)")
class OutboxIntegrationTest {

    // 테스트 상수가 빈 설정과 어긋나지 않도록 스케줄러와 같은 설정값을 주입.
    @org.springframework.beans.factory.annotation.Value("${groove.outbox.relay.max-attempts:5}")
    private int maxAttempts;

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

        // 미발행 ORDER_PAID 행 1건, 릴레이 전이라 배송은 미생성.
        assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(10)))
                .singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("ORDER_PAID"));
        assertThat(shippingRepository.findAll()).isEmpty();
        assertThat(orderStatusOf(p.orderId())).isEqualTo(OrderStatus.PAID);

        // 릴레이 → 배송 1건 + 주문 PREPARING + 미발행 0건.
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

        // 발행 표시 직전 크래시 재현: 같은 행을 미발행으로 되돌려 재전달.
        OutboxEvent row = outboxEventRepository.findAll().get(0);
        ReflectionTestUtils.setField(row, "publishedAt", null);
        outboxEventRepository.saveAndFlush(row);

        outboxRelayScheduler.relayPendingEvents();

        // 멱등이라 배송 1건, 행은 다시 발행 표시.
        assertThat(shippingRepository.findAll()).hasSize(1);
        assertThat(outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(10))).isEmpty();
    }

    @Test
    @DisplayName("영구 실패(poison) 이벤트는 재시도 상한 후 DLQ 격리되어 정상 이벤트 처리를 막지 않는다 (#268)")
    void poisonEvent_excludedAfterMaxAttempts_doesNotBlockHealthy() {
        // poison: payload 가 잘못된 JSON 이라 ORDER_PAID 핸들러 역직렬화가 매번 실패(Jackson3 = unchecked).
        OutboxEvent poison = outboxEventRepository.saveAndFlush(
                OutboxEvent.of("ORDER", 999L, "ORDER_PAID", "not-json"));

        // healthy: 정상 콜백으로 발행되는 ORDER_PAID 행.
        Payable p = persistPayableOrder();
        paymentCallbackService.applyResult(p.pgTx(), PaymentStatus.PAID, null);

        // 상한만큼 릴레이 반복: healthy 는 1회차에 발행, poison 은 매회 카운터 증가.
        for (int i = 0; i < maxAttempts; i++) {
            outboxRelayScheduler.relayPendingEvents();
        }

        assertThat(shippingRepository.findAll()).hasSize(1);
        assertThat(orderStatusOf(p.orderId())).isEqualTo(OrderStatus.PREPARING);

        // poison 은 상한 도달로 릴레이 대상에서 제외(DLQ 격리)되나 미발행 행으로 잔존.
        OutboxEvent reloaded = outboxEventRepository.findById(poison.getId()).orElseThrow();
        assertThat(reloaded.getAttemptCount()).isEqualTo(maxAttempts);
        assertThat(reloaded.getPublishedAt()).isNull();
        var relayableIds = outboxEventRepository
                .findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(maxAttempts, Limit.of(100))
                .stream().map(OutboxEvent::getId).toList();
        assertThat(relayableIds).doesNotContain(poison.getId());
        var unpublishedIds = outboxEventRepository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(100))
                .stream().map(OutboxEvent::getId).toList();
        assertThat(unpublishedIds).contains(poison.getId());

        // DLQ 가시성: 격리 조회/카운트가 poison 행을 잡아내 운영자가 쿼리 가능.
        var dlqIds = outboxEventRepository
                .findByPublishedAtIsNullAndAttemptCountGreaterThanEqualOrderByIdAsc(maxAttempts, Limit.of(100))
                .stream().map(OutboxEvent::getId).toList();
        assertThat(dlqIds).contains(poison.getId());
        assertThat(outboxEventRepository.countByPublishedAtIsNullAndAttemptCountGreaterThanEqual(maxAttempts))
                .isEqualTo(1L);
    }
}
