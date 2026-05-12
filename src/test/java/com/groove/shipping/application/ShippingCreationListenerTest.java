package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.event.OrderPaidEvent;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingCreationListener — OrderPaidEvent 구독 → 배송 생성")
class ShippingCreationListenerTest {

    private static final OrderPaidEvent EVENT = new OrderPaidEvent(7L, "ORD-20260512-A1B2C3", 1L, 42L);
    private static final String TRACKING = "8a4f0c2e-1234-4abc-9def-0123456789ab";

    @Mock
    private ShippingRepository shippingRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TrackingNumberGenerator trackingNumberGenerator;

    private ShippingCreationListener listener;

    @BeforeEach
    void setUp() {
        listener = new ShippingCreationListener(shippingRepository, orderRepository, trackingNumberGenerator);
    }

    @Test
    @DisplayName("정상 — 주문 배송지 스냅샷으로 PREPARING 배송 생성 + 운송장 발급")
    void createsPreparingShipping() {
        Order order = OrderFixtures.memberOrder("ORD-20260512-A1B2C3", 1L);
        given(shippingRepository.existsByOrderId(7L)).willReturn(false);
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(trackingNumberGenerator.generate()).willReturn(TRACKING);

        listener.onOrderPaid(EVENT);

        ArgumentCaptor<Shipping> captor = ArgumentCaptor.forClass(Shipping.class);
        verify(shippingRepository).saveAndFlush(captor.capture());
        Shipping saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(saved.getTrackingNumber()).isEqualTo(TRACKING);
        assertThat(saved.getRecipientName()).isEqualTo(order.getShippingInfo().recipientName());
        assertThat(saved.getZipCode()).isEqualTo(order.getShippingInfo().zipCode());
    }

    @Test
    @DisplayName("이미 배송이 있으면 (중복 이벤트) 새로 만들지 않는다")
    void skipsWhenShippingAlreadyExists() {
        given(shippingRepository.existsByOrderId(7L)).willReturn(true);

        listener.onOrderPaid(EVENT);

        verify(orderRepository, never()).findById(any());
        verify(shippingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("주문이 없으면 (정합성 깨짐) 건너뛴다 — 예외 없음")
    void skipsWhenOrderMissing() {
        given(shippingRepository.existsByOrderId(7L)).willReturn(false);
        given(orderRepository.findById(7L)).willReturn(Optional.empty());

        assertThatCode(() -> listener.onOrderPaid(EVENT)).doesNotThrowAnyException();

        verify(shippingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UNIQUE 충돌(DataIntegrityViolationException) 은 흡수한다")
    void swallowsUniqueViolation() {
        Order order = OrderFixtures.memberOrder("ORD-20260512-A1B2C3", 1L);
        given(shippingRepository.existsByOrderId(7L)).willReturn(false);
        given(orderRepository.findById(7L)).willReturn(Optional.of(order));
        given(trackingNumberGenerator.generate()).willReturn(TRACKING);
        given(shippingRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_shipping_order"));

        assertThatCode(() -> listener.onOrderPaid(EVENT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("그 외 RuntimeException 도 흡수한다 (AFTER_COMMIT 리스너 격리)")
    void swallowsUnexpectedException() {
        given(shippingRepository.existsByOrderId(7L)).willThrow(new RuntimeException("boom"));

        assertThatCode(() -> listener.onOrderPaid(EVENT)).doesNotThrowAnyException();
    }
}
