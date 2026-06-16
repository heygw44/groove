package com.groove.shipping.application;

import com.groove.common.outbox.OutboxEvent;
import com.groove.order.event.OrderPaidEvent;
import com.groove.shipping.domain.ShippingRepository;
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
 * OrderPaid 아웃박스 컨슈머 단위 테스트 — payload 역직렬화 → ShippingProvisioner 위임, 멱등(중복 흡수) 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaidOutboxHandler — ORDER_PAID 아웃박스 이벤트 → 배송 생성 위임")
class OrderPaidOutboxHandlerTest {

    private static final OrderPaidEvent EVENT = new OrderPaidEvent(7L, "ORD-20260512-A1B2C3", 1L, 42L);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ShippingProvisioner provisioner;
    @Mock
    private ShippingRepository shippingRepository;

    private OrderPaidOutboxHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderPaidOutboxHandler(provisioner, shippingRepository, objectMapper);
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
    @DisplayName("충돌 후 해당 주문 배송이 이미 존재하면 흡수한다 — 중복 이벤트/경합 (멱등, 발행 완료 처리)")
    void swallowsViolation_whenShippingAlreadyExists() {
        given(provisioner.provisionForOrder(EVENT.orderId(), EVENT.orderNumber()))
                .willThrow(new DataIntegrityViolationException("uk_shipping_order"));
        given(shippingRepository.existsByOrderId(EVENT.orderId())).willReturn(true);

        assertThatCode(() -> handler.handle(outboxEvent())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("충돌인데 해당 주문 배송이 없으면(운송장 중복 등 다른 제약 위반) 전파한다 — 미생성 유실 방지, 재시도")
    void rethrowsViolation_whenShippingNotCreated() {
        given(provisioner.provisionForOrder(EVENT.orderId(), EVENT.orderNumber()))
                .willThrow(new DataIntegrityViolationException("uk_shipping_tracking"));
        given(shippingRepository.existsByOrderId(EVENT.orderId())).willReturn(false);

        assertThatThrownBy(() -> handler.handle(outboxEvent()))
                .isInstanceOf(DataIntegrityViolationException.class);
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
