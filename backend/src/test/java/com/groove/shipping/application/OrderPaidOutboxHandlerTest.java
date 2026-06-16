package com.groove.shipping.application;

import com.groove.common.outbox.OutboxEvent;
import com.groove.order.event.OrderPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * OrderPaid 아웃박스 컨슈머 단위 테스트 (#237) — payload 역직렬화 → ShippingProvisioner 위임, 멱등(중복 흡수) 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaidOutboxHandler — ORDER_PAID 아웃박스 이벤트 → 배송 생성 위임")
class OrderPaidOutboxHandlerTest {

    private static final OrderPaidEvent EVENT = new OrderPaidEvent(7L, "ORD-20260512-A1B2C3", 1L, 42L);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShippingProvisioner provisioner;

    private OrderPaidOutboxHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderPaidOutboxHandler(provisioner, objectMapper);
    }

    private OutboxEvent outboxEvent() {
        return OutboxEvent.of(OrderPaidEvent.OUTBOX_AGGREGATE_TYPE, EVENT.orderId(), OrderPaidEvent.OUTBOX_EVENT_TYPE,
                objectMapper.writeValueAsString(EVENT));
    }

    @Test
    @DisplayName("eventType 은 ORDER_PAID")
    void eventType() {
        assertThat(handler.eventType()).isEqualTo(OrderPaidEvent.OUTBOX_EVENT_TYPE);
    }

    @Test
    @DisplayName("payload 를 역직렬화해 orderId/orderNumber 로 프로비저너에 위임한다")
    void delegatesToProvisioner() {
        handler.handle(outboxEvent());

        verify(provisioner).provisionForOrder(EVENT.orderId(), EVENT.orderNumber());
    }

    @Test
    @DisplayName("UNIQUE 충돌(DataIntegrityViolationException) 은 흡수한다 — 중복 이벤트/경합 (멱등, 발행 완료 처리)")
    void swallowsUniqueViolation() {
        given(provisioner.provisionForOrder(EVENT.orderId(), EVENT.orderNumber()))
                .willThrow(new DataIntegrityViolationException("uk_shipping_order"));

        assertThatCode(() -> handler.handle(outboxEvent())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("그 외 RuntimeException 은 전파한다 — 릴레이가 다음 주기에 재시도(at-least-once)")
    void propagatesUnexpectedException() {
        given(provisioner.provisionForOrder(EVENT.orderId(), EVENT.orderNumber()))
                .willThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> handler.handle(outboxEvent()))
                .isInstanceOf(RuntimeException.class);
    }
}
