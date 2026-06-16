package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingProvisioner — 배송 생성(프로비저닝) 공유 로직")
class ShippingProvisionerTest {

    private static final Long ORDER_ID = 7L;
    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";
    private static final String TRACKING = "8a4f0c2e-1234-4abc-9def-0123456789ab";

    @Mock
    private ShippingRepository shippingRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TrackingNumberGenerator trackingNumberGenerator;

    private ShippingProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new ShippingProvisioner(shippingRepository, orderRepository, trackingNumberGenerator);
    }

    @Test
    @DisplayName("정상 — PREPARING 배송 생성 + 운송장 발급 + 주문도 PAID→PREPARING 락스텝 전이, true 반환")
    void createsPreparingShipping() {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, 1L);
        order.changeStatus(OrderStatus.PAID, null); // 결제 직후 상태를 재현
        given(shippingRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(trackingNumberGenerator.generate()).willReturn(TRACKING);

        boolean created = provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER);

        assertThat(created).isTrue();
        ArgumentCaptor<Shipping> captor = ArgumentCaptor.forClass(Shipping.class);
        verify(shippingRepository).saveAndFlush(captor.capture());
        Shipping saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(saved.getTrackingNumber()).isEqualTo(TRACKING);
        assertThat(saved.getRecipientName()).isEqualTo(order.getShippingInfo().recipientName());
        assertThat(saved.getZipCode()).isEqualTo(order.getShippingInfo().zipCode());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("주문이 종착(취소·환불)이면 배송을 만들지 않고 false 반환 — 프로비저닝 race 가드 (#233)")
    void skipsWhenOrderTerminal() {
        // OrderPaid 아웃박스 이벤트가 릴레이되는 사이 별도 트랜잭션의 환불로 주문이 이미 CANCELLED 가 된 race 재현.
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, 1L);
        order.changeStatus(OrderStatus.CANCELLED, "환불");
        given(shippingRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        boolean created = provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER);

        assertThat(created).isFalse();
        verify(shippingRepository, never()).saveAndFlush(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 배송이 있으면 새로 만들지 않고 false 반환 (주문 조회조차 하지 않음)")
    void skipsWhenShippingAlreadyExists() {
        given(shippingRepository.existsByOrderId(ORDER_ID)).willReturn(true);

        boolean created = provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER);

        assertThat(created).isFalse();
        verify(orderRepository, never()).findById(any());
        verify(shippingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("주문이 없으면(정합성 깨짐) 건너뛰고 false 반환")
    void skipsWhenOrderMissing() {
        given(shippingRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        boolean created = provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER);

        assertThat(created).isFalse();
        verify(shippingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 PII 익명화된 주문이면(#188 심층 방어) 배송을 만들지 않고 false 반환 — 배송지가 '익명' 으로 마스킹됨")
    void skipsWhenOrderAlreadyAnonymized() {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, 1L);
        order.anonymizePii(java.time.Instant.parse("2026-03-01T00:00:00Z"));
        given(shippingRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        boolean created = provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER);

        assertThat(created).isFalse();
        verify(shippingRepository, never()).saveAndFlush(any());
        verify(trackingNumberGenerator, never()).generate();
    }

    @Test
    @DisplayName("UNIQUE 충돌(DataIntegrityViolationException) 은 삼키지 않고 호출자로 전파한다")
    void propagatesUniqueViolation() {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, 1L);
        given(shippingRepository.existsByOrderId(ORDER_ID)).willReturn(false);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(trackingNumberGenerator.generate()).willReturn(TRACKING);
        given(shippingRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("uk_shipping_order"));

        assertThatThrownBy(() -> provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("그 외 RuntimeException 도 삼키지 않고 전파한다 (호출자가 재시도 정책 결정)")
    void propagatesUnexpectedException() {
        given(shippingRepository.existsByOrderId(ORDER_ID)).willThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> provisioner.provisionForOrder(ORDER_ID, ORDER_NUMBER))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }
}
