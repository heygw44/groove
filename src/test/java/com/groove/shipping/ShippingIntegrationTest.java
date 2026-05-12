package com.groove.shipping;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.event.OrderPaidEvent;
import com.groove.shipping.application.ShippingProgressScheduler;
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
import org.springframework.context.ApplicationEventPublisher;
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
 *   <li>{@code OrderPaidEvent} 가 커밋되면 {@code ShippingCreationListener} 가 PREPARING 배송을 만들고 운송장을 발급한다.</li>
 *   <li>같은 이벤트가 다시 와도 배송은 1건 (uk_shipping_order + existsByOrderId 가드).</li>
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
    private ApplicationEventPublisher publisher;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private ShippingProgressScheduler progressScheduler;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        shippingRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    private Order persistGuestOrder() {
        return orderRepository.saveAndFlush(
                OrderFixtures.guestOrder("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                        "guest@example.com", "01099998888"));
    }

    private void publishOrderPaid(Order order) {
        tx.executeWithoutResult(s ->
                publisher.publishEvent(new OrderPaidEvent(order.getId(), order.getOrderNumber(), order.getMemberId(), 1L)));
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

        // 같은 이벤트 재전달 → 여전히 1건
        publishOrderPaid(order);
        assertThat(shippingRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("자동 진행 스케줄러 — PREPARING → SHIPPED → DELIVERED 한 단계씩, 종착 후 무변화")
    void progressScheduler_advancesOneStepPerRun() {
        Order order = persistGuestOrder();
        publishOrderPaid(order);
        Long shippingId = shippingRepository.findAll().get(0).getId();

        progressScheduler.progressShipments();
        Shipping afterFirst = shippingRepository.findById(shippingId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(afterFirst.getShippedAt()).isNotNull();
        assertThat(afterFirst.getDeliveredAt()).isNull();

        progressScheduler.progressShipments();
        Shipping afterSecond = shippingRepository.findById(shippingId).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(afterSecond.getDeliveredAt()).isNotNull();

        progressScheduler.progressShipments();
        assertThat(shippingRepository.findById(shippingId).orElseThrow().getStatus()).isEqualTo(ShippingStatus.DELIVERED);
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
}
