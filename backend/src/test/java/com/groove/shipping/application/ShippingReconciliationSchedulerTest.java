package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingReconciliationScheduler 단위 테스트")
class ShippingReconciliationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");
    private static final Duration MIN_AGE = Duration.ofMinutes(2);
    private static final int BATCH_SIZE = 200;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ShippingProvisioner provisioner;

    private ShippingReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ShippingReconciliationScheduler(orderRepository, provisioner,
                Clock.fixed(NOW, ZoneOffset.UTC), MIN_AGE, BATCH_SIZE);
    }

    private static Order paidOrder(Long id, String orderNumber) {
        Order order = OrderFixtures.memberOrder(orderNumber, 1L);
        order.changeStatus(OrderStatus.PAID, null);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    @DisplayName("PAID·배송없음 고아를 프로비저너로 보충한다 (cutoff = now - min-age, batch-size 만큼 조회)")
    void reconcile_orphanFound_provisions() {
        given(orderRepository.findByStatusAndPaidAtBefore(
                eq(OrderStatus.PAID), eq(NOW.minus(MIN_AGE)), eq(Limit.of(BATCH_SIZE))))
                .willReturn(List.of(paidOrder(7L, "ORD-A")));
        given(provisioner.provisionForOrder(7L, "ORD-A")).willReturn(true);

        scheduler.reconcileOrphanedOrders();

        verify(provisioner).provisionForOrder(7L, "ORD-A");
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 보충한다 (스케줄러 스레드로 예외 미전파)")
    void reconcile_oneFailure_continues() {
        given(orderRepository.findByStatusAndPaidAtBefore(any(), any(), any()))
                .willReturn(List.of(paidOrder(1L, "ORD-BAD"), paidOrder(2L, "ORD-GOOD")));
        given(provisioner.provisionForOrder(1L, "ORD-BAD")).willThrow(new RuntimeException("DB 일시 장애"));
        given(provisioner.provisionForOrder(2L, "ORD-GOOD")).willReturn(true);

        assertThatCode(() -> scheduler.reconcileOrphanedOrders()).doesNotThrowAnyException();

        verify(provisioner).provisionForOrder(2L, "ORD-GOOD");
    }

    @Test
    @DisplayName("대상이 없으면 프로비저너를 건드리지 않는다")
    void reconcile_noOrphans_noop() {
        given(orderRepository.findByStatusAndPaidAtBefore(any(), any(), any())).willReturn(List.of());

        scheduler.reconcileOrphanedOrders();

        verifyNoInteractions(provisioner);
    }

    @Test
    @DisplayName("batch-size 가 0 이하면 생성 시점에 거부한다")
    void rejectsNonPositiveBatchSize() {
        assertThatCode(() -> new ShippingReconciliationScheduler(orderRepository, provisioner,
                Clock.fixed(NOW, ZoneOffset.UTC), MIN_AGE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(provisioner, never()).provisionForOrder(any(), any());
    }
}
