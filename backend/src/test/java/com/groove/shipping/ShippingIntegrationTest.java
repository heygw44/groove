package com.groove.shipping;

import com.groove.common.outbox.OutboxEventPublisher;
import com.groove.common.outbox.OutboxRelayScheduler;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.event.OrderPaidEvent;
import com.groove.shipping.application.ShippingProgressScheduler;
import com.groove.shipping.application.ShippingReconciliationScheduler;
import com.groove.shipping.application.ShippingService;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 배송 E2E 통합 테스트 (#W7-6) — Testcontainers MySQL 위에서 실제 이벤트/트랜잭션/스케줄러를 돌린다.
 *
 * <ul>
 *   <li>{@code OrderPaidEvent} 가 아웃박스에 기록·커밋되면 릴레이({@code OutboxRelayScheduler})가
 *       {@code OrderPaidOutboxHandler} 로 디스패치해 PREPARING 배송을 만들고 운송장을 발급한다 (#237).</li>
 *   <li>같은 이벤트가 다시 발행돼도 배송은 1건 (uk_shipping_order + existsByOrderId 가드 — 멱등 컨슈머).</li>
 *   <li>자동 진행 스케줄러가 PREPARING → SHIPPED → DELIVERED 로 한 단계씩 민다 (테스트 프로파일은 delay 0).</li>
 *   <li>{@code GET /api/v1/shippings/{trackingNumber}} 가 배송을 조회하고, 미존재/형식 위반은 404/400.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("배송 E2E — 이벤트 구독 / 자동 진행 / 운송장 조회 (#W7-6)")
class ShippingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ShippingRepository shippingRepository;
    @Autowired
    private OutboxEventPublisher outboxEventPublisher;
    @Autowired
    private OutboxRelayScheduler outboxRelayScheduler;
    @Autowired
    private com.groove.common.outbox.OutboxEventRepository outboxEventRepository;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private ShippingProgressScheduler progressScheduler;
    @Autowired
    private ShippingReconciliationScheduler reconciliationScheduler;
    @Autowired
    private ShippingService shippingService;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        outboxEventRepository.deleteAllInBatch();
        shippingRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    private Order persistGuestOrder() {
        return orderRepository.saveAndFlush(
                OrderFixtures.guestOrder("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "guest@example.com", "01099998888"));
    }

    /**
     * 결제 콜백을 재현한다 — 같은 트랜잭션에서 주문을 PAID 로 전이시키고 {@code OrderPaidEvent} 를 아웃박스에 기록한다
     * ({@code PaymentCallbackService.applyResult} 와 동일, #237). 이어 릴레이를 직접 돌려 미발행 이벤트를 컨슈머
     * ({@code OrderPaidOutboxHandler})에 디스패치하면 배송이 생성되고 주문이 PREPARING 으로 락스텝 전진한다. 멱등
     * 호출(같은 이벤트 재발행)에 대비해 이미 PENDING 이 아니면 전이를 건너뛴다.
     */
    private void publishOrderPaid(Order order) {
        tx.executeWithoutResult(s -> {
            Order managed = orderRepository.findById(order.getId()).orElseThrow();
            if (managed.getStatus() == OrderStatus.PENDING) {
                managed.changeStatus(OrderStatus.PAID, null);
            }
            outboxEventPublisher.publish(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, managed.getId(),
                    OrderPaidEvent.OUTBOX_EVENT_TYPE,
                    new OrderPaidEvent(managed.getId(), managed.getOrderNumber(), managed.getMemberId(), 1L));
        });
        outboxRelayScheduler.relayPendingEvents();
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    /**
     * 결제는 PAID 로 커밋됐지만 AFTER_COMMIT 배송 생성이 실패한 "고아 주문"을 재현한다 — 이벤트를 발행하지 않고
     * 주문만 PAID 로 전이시켜 {@code paid_at} 을 찍는다(=리스너 미동작). reconciliation 의 보충 대상이 된다.
     */
    private void markPaidWithoutEvent(Order order) {
        tx.executeWithoutResult(s -> {
            Order managed = orderRepository.findById(order.getId()).orElseThrow();
            managed.changeStatus(OrderStatus.PAID, null);
        });
    }

    @Test
    @DisplayName("결제 완료 이벤트 → PREPARING 배송 생성 + 배송지 스냅샷 복사, 중복 이벤트는 무해")
    void orderPaid_createsShipping_idempotent() {
        Order order = persistGuestOrder();

        publishOrderPaid(order);

        assertThat(shippingRepository.findAll()).hasSize(1);
        Shipping shipping = shippingRepository.findAll().get(0);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getTrackingNumber()).isNotBlank();
        assertThat(shipping.getOrder().getId()).isEqualTo(order.getId());
        assertThat(shipping.getRecipientName()).isEqualTo("김철수");
        assertThat(shipping.getZipCode()).isEqualTo("06234");
        assertThat(shipping.isSafePackagingRequested()).isFalse();
        // 배송 생성에 맞춰 주문도 PREPARING 으로 락스텝 전진 (이슈 #161)
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PREPARING);

        // 같은 이벤트 재전달 → 여전히 1건, 주문도 PREPARING 유지(중복 전이 없음)
        publishOrderPaid(order);
        assertThat(shippingRepository.findAll()).hasSize(1);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("reconciliation — PAID 인데 배송이 없는 고아 주문을 보충 생성하고 PREPARING 으로 전진, 재실행은 무해 (이슈 #169)")
    void reconciliation_provisionsOrphanedPaidOrder() {
        Order order = persistGuestOrder();
        // 리스너가 배송 생성에 실패한 상황 재현 — PAID 인데 배송 없음
        markPaidWithoutEvent(order);
        assertThat(shippingRepository.findAll()).isEmpty();
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PAID);

        reconciliationScheduler.reconcileOrphanedOrders();

        assertThat(shippingRepository.findAll()).hasSize(1);
        Shipping shipping = shippingRepository.findAll().get(0);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getTrackingNumber()).isNotBlank();
        assertThat(shipping.getOrder().getId()).isEqualTo(order.getId());
        // 보충 생성에 맞춰 주문도 PREPARING 으로 락스텝 전진 → 더 이상 보충 대상이 아니다
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PREPARING);

        // 재실행해도 대상이 비어 새로 만들지 않는다 (멱등)
        reconciliationScheduler.reconcileOrphanedOrders();
        assertThat(shippingRepository.findAll()).hasSize(1);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("자동 진행 스케줄러 — 배송·주문이 락스텝으로 SHIPPED → DELIVERED, 종착 후 무변화 (이슈 #161)")
    void progressScheduler_advancesOneStepPerRun() {
        Order order = persistGuestOrder();
        publishOrderPaid(order);
        Long shippingId = shippingRepository.findAll().get(0).getId();
        // 결제→배송 생성 직후: 배송·주문 모두 PREPARING
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.PREPARING);

        progressScheduler.progressShipments();
        Shipping afterFirst = shippingRepository.findById(shippingId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(afterFirst.getShippedAt()).isNotNull();
        assertThat(afterFirst.getDeliveredAt()).isNull();
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.SHIPPED);

        progressScheduler.progressShipments();
        Shipping afterSecond = shippingRepository.findById(shippingId).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(afterSecond.getDeliveredAt()).isNotNull();
        // 배송 완료 → 주문도 DELIVERED 에 도달해 리뷰 작성 자격을 만족한다
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.DELIVERED);

        progressScheduler.progressShipments();
        assertThat(shippingRepository.findById(shippingId).orElseThrow().getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("GET /shippings/{trackingNumber} — 존재하면 200 + 바디")
    void getByTrackingNumber_returns200() throws Exception {
        Order order = persistGuestOrder();
        publishOrderPaid(order);
        String tracking = shippingRepository.findAll().get(0).getTrackingNumber();

        mockMvc.perform(get("/api/v1/shippings/" + tracking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(tracking))
                .andExpect(jsonPath("$.status").value(ShippingStatus.PREPARING.name()))
                .andExpect(jsonPath("$.recipientName").value("김철수"))
                .andExpect(jsonPath("$.address").value("서울시 강남구 테헤란로 123"))
                .andExpect(jsonPath("$.safePackagingRequested").value(false))
                .andExpect(jsonPath("$.shippedAt").doesNotExist())
                .andExpect(jsonPath("$.deliveredAt").doesNotExist());
    }

    @Test
    @DisplayName("GET /shippings/{trackingNumber} — 미존재 운송장 → 404 SHIPPING_NOT_FOUND")
    void getByTrackingNumber_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/shippings/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPPING_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /shippings/{trackingNumber} — 형식 위반 path → 400")
    void getByTrackingNumber_malformed_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/shippings/not!a!tracking"))
                .andExpect(status().isBadRequest());
    }

    /** 주문을 종착(취소)으로 전이시킨다 — 발송 전 환불의 주문 측 효과 재현. */
    private void cancelOrder(Order order) {
        tx.executeWithoutResult(s -> {
            Order managed = orderRepository.findById(order.getId()).orElseThrow();
            managed.changeStatus(OrderStatus.CANCELLED, "환불");
        });
    }

    @Test
    @DisplayName("발송 전 환불로 배송이 CANCELLED 되면 자동 진행 스케줄러가 DELIVERED 로 밀지 않는다 (#233)")
    void cancelledShipping_notAdvancedByScheduler() {
        Order order = persistGuestOrder();
        publishOrderPaid(order); // PREPARING 배송 + 주문 PREPARING
        Long shippingId = shippingRepository.findAll().get(0).getId();

        // refund 효과 재현: 주문 CANCELLED + 배송 CANCELLED 동기화(ShippingService.cancelForOrder)
        cancelOrder(order);
        shippingService.cancelForOrder(order.getId());
        assertThat(shippingRepository.findById(shippingId).orElseThrow().getStatus()).isEqualTo(ShippingStatus.CANCELLED);

        // 스케줄러를 여러 번 돌려도 CANCELLED 유지 — 어떤 경로로도 DELIVERED 로 가지 않는다 (AC)
        progressScheduler.progressShipments();
        progressScheduler.progressShipments();
        progressScheduler.progressShipments();

        assertThat(shippingRepository.findById(shippingId).orElseThrow().getStatus()).isEqualTo(ShippingStatus.CANCELLED);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("주문이 종착(취소)인데 배송이 PREPARING 으로 남아도 스케줄러가 전진시키지 않는다 (#233 잔여 윈도우 가드)")
    void terminalOrder_preparingShipping_notAdvanced() {
        Order order = persistGuestOrder();
        publishOrderPaid(order); // PREPARING 배송 + 주문 PREPARING
        Long shippingId = shippingRepository.findAll().get(0).getId();

        // 배송 취소 동기화가 누락된 잔여 윈도우 재현: 주문만 CANCELLED, 배송은 PREPARING 유지
        cancelOrder(order);

        progressScheduler.progressShipments();
        progressScheduler.progressShipments();

        // 종착 주문 가드가 advanceToShipped 를 막아 배송이 PREPARING 그대로 — DELIVERED 로 새지 않는다
        assertThat(shippingRepository.findById(shippingId).orElseThrow().getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 CANCELLED 된 주문에 OrderPaid 이벤트가 와도 배송을 만들지 않는다 (#233 프로비저닝 가드)")
    void cancelledOrder_noShippingProvisioned() {
        Order order = persistGuestOrder();
        // 발송 전 환불로 주문이 먼저 CANCELLED 가 된 뒤, 늦게 도착한 OrderPaid 아웃박스 이벤트가 릴레이되는 race
        cancelOrder(order);

        tx.executeWithoutResult(s -> outboxEventPublisher.publish(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, order.getId(),
                OrderPaidEvent.OUTBOX_EVENT_TYPE,
                new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), 1L)));
        outboxRelayScheduler.relayPendingEvents();

        // 프로비저닝 가드가 종착 주문에 배송을 만들지 않는다
        assertThat(shippingRepository.findByOrderId(order.getId())).isEmpty();
        assertThat(orderStatus(order.getId())).isEqualTo(OrderStatus.CANCELLED);
    }
}
