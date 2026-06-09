package com.groove.shipping.application;

import com.groove.order.event.OrderPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingCreationListener — OrderPaidEvent 구독 → ShippingProvisioner 위임 + 실패 격리")
class ShippingCreationListenerTest {

    private static final OrderPaidEvent EVENT = new OrderPaidEvent(7L, "ORD-20260512-A1B2C3", 1L, 42L);

    @Mock
    private ShippingProvisioner provisioner;

    private ShippingCreationListener listener;

    @BeforeEach
    void setUp() {
        listener = new ShippingCreationListener(provisioner);
    }

    @Test
    @DisplayName("이벤트의 orderId/orderNumber 로 프로비저너에 위임한다")
    void delegatesToProvisioner() {
        listener.onOrderPaid(EVENT);

        verify(provisioner).provisionForOrder(EVENT.orderId(), EVENT.orderNumber());
    }

    @Test
    @DisplayName("UNIQUE 충돌(DataIntegrityViolationException) 은 흡수한다 — 중복 이벤트/경합")
    void swallowsUniqueViolation() {
        given(provisioner.provisionForOrder(EVENT.orderId(), EVENT.orderNumber()))
                .willThrow(new DataIntegrityViolationException("uk_shipping_order"));

        assertThatCode(() -> listener.onOrderPaid(EVENT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("그 외 RuntimeException 도 흡수한다 (AFTER_COMMIT 리스너 격리, 보충은 reconciliation 이 담당)")
    void swallowsUnexpectedException() {
        given(provisioner.provisionForOrder(EVENT.orderId(), EVENT.orderNumber()))
                .willThrow(new RuntimeException("boom"));

        assertThatCode(() -> listener.onOrderPaid(EVENT)).doesNotThrowAnyException();
    }
}
