package com.groove.shipping.application;

import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPiiAnonymizationScheduler 단위 테스트")
class OrderPiiAnonymizationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-06-09T10:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(90);
    private static final int BATCH_SIZE = 200;
    private static final Set<OrderStatus> TERMINAL_STATUSES =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED);

    @Mock
    private ShippingRepository shippingRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderPiiAnonymizer anonymizer;

    private OrderPiiAnonymizationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderPiiAnonymizationScheduler(
                shippingRepository, orderRepository, anonymizer, Clock.fixed(NOW, ZoneOffset.UTC), RETENTION, BATCH_SIZE);
    }

    /** ShippingIdView 를 람다로 구성한다. */
    private static ShippingRepository.ShippingIdView view(long id) {
        return () -> id;
    }

    /** OrderNumberView 를 익명 클래스로 구성한다. */
    private static OrderRepository.OrderNumberView orderView(long id) {
        return new OrderRepository.OrderNumberView() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getOrderNumber() {
                return "ORD-" + id;
            }
        };
    }

    @Test
    @DisplayName("배송완료+보존기간 경과 건을 cutoff=now-retention, batch-size 만큼 조회해 건별 익명화한다")
    void anonymize_deliveredPastRetention_anonymizesEach() {
        given(shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                eq(ShippingStatus.DELIVERED), eq(NOW.minus(RETENTION)), eq(Limit.of(BATCH_SIZE))))
                .willReturn(List.of(view(10L), view(20L)));

        scheduler.anonymizeDeliveredOrders();

        verify(anonymizer).anonymizeForShipping(10L, NOW);
        verify(anonymizer).anonymizeForShipping(20L, NOW);
    }

    @Test
    @DisplayName("대상이 없으면 익명화기를 건드리지 않는다")
    void anonymize_noTargets_noop() {
        given(shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                any(), any(), any()))
                .willReturn(List.of());

        scheduler.anonymizeDeliveredOrders();

        verifyNoInteractions(anonymizer);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 처리한다 (스케줄러 스레드로 예외 미전파)")
    void anonymize_oneFailure_continues() {
        given(shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                any(), any(), any()))
                .willReturn(List.of(view(1L), view(2L)));
        given(anonymizer.anonymizeForShipping(1L, NOW)).willThrow(new RuntimeException("일시 장애"));
        given(anonymizer.anonymizeForShipping(2L, NOW)).willReturn(true);

        assertThatCode(() -> scheduler.anonymizeDeliveredOrders()).doesNotThrowAnyException();

        verify(anonymizer).anonymizeForShipping(2L, NOW);
    }

    @Test
    @DisplayName("batch-size 가 0 이하면 생성자에서 IllegalArgumentException")
    void constructor_nonPositiveBatchSize_throws() {
        assertThatThrownBy(() -> new OrderPiiAnonymizationScheduler(
                shippingRepository, orderRepository, anonymizer, Clock.fixed(NOW, ZoneOffset.UTC), RETENTION, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retention 이 0/음수면 생성자에서 IllegalArgumentException (조기 익명화 방지)")
    void constructor_nonPositiveRetention_throws() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        assertThatThrownBy(() -> new OrderPiiAnonymizationScheduler(
                shippingRepository, orderRepository, anonymizer, clock, Duration.ZERO, BATCH_SIZE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderPiiAnonymizationScheduler(
                shippingRepository, orderRepository, anonymizer, clock, Duration.ofDays(-1), BATCH_SIZE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비배송 종착 주문(PENDING/PAYMENT_FAILED/CANCELLED)을 cutoff=now-retention, batch-size 만큼 조회해 건별 익명화한다")
    void anonymizeTerminal_pastRetention_anonymizesEach() {
        given(orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                eq(TERMINAL_STATUSES), eq(NOW.minus(RETENTION)), eq(Limit.of(BATCH_SIZE))))
                .willReturn(List.of(orderView(30L), orderView(40L)));

        scheduler.anonymizeTerminalNonShippingOrders();

        verify(anonymizer).anonymizeOrder(30L, NOW);
        verify(anonymizer).anonymizeOrder(40L, NOW);
    }

    @Test
    @DisplayName("비배송 종착 대상이 없으면 익명화기를 건드리지 않는다")
    void anonymizeTerminal_noTargets_noop() {
        given(orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                any(), any(), any()))
                .willReturn(List.of());

        scheduler.anonymizeTerminalNonShippingOrders();

        verifyNoInteractions(anonymizer);
    }

    @Test
    @DisplayName("비배송 종착 — 한 건이 실패해도 나머지를 계속 처리한다 (스케줄러 스레드로 예외 미전파)")
    void anonymizeTerminal_oneFailure_continues() {
        given(orderRepository.findByStatusInAndAnonymizedAtIsNullAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                any(), any(), any()))
                .willReturn(List.of(orderView(1L), orderView(2L)));
        given(anonymizer.anonymizeOrder(1L, NOW)).willThrow(new RuntimeException("일시 장애"));
        given(anonymizer.anonymizeOrder(2L, NOW)).willReturn(true);

        assertThatCode(() -> scheduler.anonymizeTerminalNonShippingOrders()).doesNotThrowAnyException();

        verify(anonymizer).anonymizeOrder(2L, NOW);
    }
}
