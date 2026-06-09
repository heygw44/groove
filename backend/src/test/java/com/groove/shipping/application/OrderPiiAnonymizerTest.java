package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPiiAnonymizer — 주문/배송 PII 마스킹 공유 로직")
class OrderPiiAnonymizerTest {

    private static final Instant NOW = Instant.parse("2026-06-09T10:00:00Z");
    private static final long SHIPPING_ID = 5L;
    private static final long ORDER_ID = 7L;

    @Mock
    private ShippingRepository shippingRepository;
    @Mock
    private OrderRepository orderRepository;

    private OrderPiiAnonymizer anonymizer;

    @BeforeEach
    void setUp() {
        anonymizer = new OrderPiiAnonymizer(shippingRepository, orderRepository);
    }

    /** 배송완료(DELIVERED) 상태의 배송을 주문과 함께 구성한다. */
    private static Shipping deliveredShipping(Order order) {
        Shipping shipping = Shipping.prepare(order, order.getShippingInfo(), "track-1");
        shipping.markShipped();
        shipping.markDelivered();
        return shipping;
    }

    @Test
    @DisplayName("게스트 주문/배송의 수령인·주소·게스트 PII 를 마스킹하고 anonymized_at 을 찍는다 (true 반환)")
    void anonymize_guestOrder_masksOrderAndShipping() {
        Order order = OrderFixtures.guestOrder("ORD-PII-1", "guest@example.com", "01099998888");
        Shipping shipping = deliveredShipping(order);
        given(shippingRepository.findWithOrderById(SHIPPING_ID)).willReturn(Optional.of(shipping));

        boolean done = anonymizer.anonymizeForShipping(SHIPPING_ID, NOW);

        assertThat(done).isTrue();
        // 주문 PII (배송지 스냅샷은 getShippingInfo 로 노출)
        assertThat(order.getShippingInfo().recipientName()).isEqualTo("익명");
        assertThat(order.getShippingInfo().recipientPhone()).isEqualTo("익명");
        assertThat(order.getShippingInfo().address()).isEqualTo("익명");
        assertThat(order.getShippingInfo().addressDetail()).isNull();
        assertThat(order.getShippingInfo().zipCode()).isEqualTo("익명");
        assertThat(order.getGuestEmail()).isNull();
        assertThat(order.getGuestPhone()).isNull();
        assertThat(order.getAnonymizedAt()).isEqualTo(NOW);
        // 배송 PII
        assertThat(shipping.getRecipientName()).isEqualTo("익명");
        assertThat(shipping.getRecipientPhone()).isEqualTo("익명");
        assertThat(shipping.getAddress()).isEqualTo("익명");
        assertThat(shipping.getZipCode()).isEqualTo("익명");
        assertThat(shipping.getAnonymizedAt()).isEqualTo(NOW);
        // 운송장 등 비-PII 는 보존
        assertThat(shipping.getTrackingNumber()).isEqualTo("track-1");
    }

    @Test
    @DisplayName("이미 익명화된 배송 → 멱등 no-op (false 반환, 시각 불변)")
    void anonymize_alreadyAnonymized_idempotentNoOp() {
        Order order = OrderFixtures.memberOrder("ORD-PII-2", 1L);
        Shipping shipping = deliveredShipping(order);
        Instant first = Instant.parse("2026-03-01T00:00:00Z");
        shipping.anonymizePii(first);
        order.anonymizePii(first);
        given(shippingRepository.findWithOrderById(SHIPPING_ID)).willReturn(Optional.of(shipping));

        boolean done = anonymizer.anonymizeForShipping(SHIPPING_ID, NOW);

        assertThat(done).isFalse();
        assertThat(shipping.getAnonymizedAt()).isEqualTo(first);
        assertThat(order.getAnonymizedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("배송이 없으면 건너뛰고 false 반환 (예외 미전파)")
    void anonymize_shippingMissing_returnsFalse() {
        given(shippingRepository.findWithOrderById(SHIPPING_ID)).willReturn(Optional.empty());

        assertThatCode(() -> assertThat(anonymizer.anonymizeForShipping(SHIPPING_ID, NOW)).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("anonymizeOrder — 배송 없는 게스트 종착 주문(PENDING)의 PII 를 마스킹한다 (배송 조회 안 함, true 반환)")
    void anonymizeOrder_guestNoShipping_masksOrder() {
        // 게스트 주문은 PENDING — 배송이 없는 종착 후보라 배송 조회를 건너뛴다.
        Order order = OrderFixtures.guestOrder("ORD-PII-3", "guest@example.com", "01099998888");
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        boolean done = anonymizer.anonymizeOrder(ORDER_ID, NOW);

        assertThat(done).isTrue();
        assertThat(order.getShippingInfo().recipientName()).isEqualTo("익명");
        assertThat(order.getShippingInfo().recipientPhone()).isEqualTo("익명");
        assertThat(order.getShippingInfo().address()).isEqualTo("익명");
        assertThat(order.getShippingInfo().addressDetail()).isNull();
        assertThat(order.getShippingInfo().zipCode()).isEqualTo("익명");
        assertThat(order.getGuestEmail()).isNull();
        assertThat(order.getGuestPhone()).isNull();
        assertThat(order.getAnonymizedAt()).isEqualTo(NOW);
        verify(shippingRepository, never()).findByOrderId(ORDER_ID);
    }

    @Test
    @DisplayName("anonymizeOrder — 환불로 취소(CANCELLED)돼 배송 행이 있으면 주문·배송 PII 를 함께 마스킹한다")
    void anonymizeOrder_cancelledWithShipping_masksOrderAndShipping() {
        Order order = OrderFixtures.memberOrder("ORD-PII-4", 1L);
        Shipping shipping = Shipping.prepare(order, order.getShippingInfo(), "track-4");
        order.changeStatus(OrderStatus.CANCELLED, "환불");
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(shippingRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(shipping));

        boolean done = anonymizer.anonymizeOrder(ORDER_ID, NOW);

        assertThat(done).isTrue();
        assertThat(order.getAnonymizedAt()).isEqualTo(NOW);
        assertThat(shipping.getRecipientName()).isEqualTo("익명");
        assertThat(shipping.getAddress()).isEqualTo("익명");
        assertThat(shipping.getAnonymizedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("anonymizeOrder — 이미 익명화된 주문이면 멱등 no-op (false 반환, 배송 조회 안 함)")
    void anonymizeOrder_alreadyAnonymized_idempotentNoOp() {
        Order order = OrderFixtures.memberOrder("ORD-PII-5", 1L);
        Instant first = Instant.parse("2026-03-01T00:00:00Z");
        order.anonymizePii(first);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        boolean done = anonymizer.anonymizeOrder(ORDER_ID, NOW);

        assertThat(done).isFalse();
        assertThat(order.getAnonymizedAt()).isEqualTo(first);
        verify(shippingRepository, never()).findByOrderId(ORDER_ID);
    }

    @Test
    @DisplayName("anonymizeOrder — 주문이 없으면 건너뛰고 false 반환")
    void anonymizeOrder_orderMissing_returnsFalse() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThat(anonymizer.anonymizeOrder(ORDER_ID, NOW)).isFalse();
    }

    @Test
    @DisplayName("anonymizeOrder — 배치 조회 후 PAID 로 전진한 주문(TOCTOU 경합)은 익명화하지 않고 false (배송 조회 안 함)")
    void anonymizeOrder_statusAdvancedToPaid_skips() {
        // 배치가 PENDING 으로 조회했지만 그 사이 결제 콜백으로 PAID 가 된 상황 — 트랜잭션 내 재검증으로 걸러져야 한다.
        Order order = OrderFixtures.memberOrder("ORD-PII-6", 1L);
        order.changeStatus(OrderStatus.PAID, null);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        boolean done = anonymizer.anonymizeOrder(ORDER_ID, NOW);

        assertThat(done).isFalse();
        assertThat(order.getAnonymizedAt()).isNull();
        verify(shippingRepository, never()).findByOrderId(ORDER_ID);
    }
}
