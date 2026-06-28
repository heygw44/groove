package com.groove.order.application;

import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderMemberWithdrawalGuard 단위 테스트")
class OrderMemberWithdrawalGuardTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("차단 상태 집합 {PAID, PREPARING, SHIPPED} 으로 조회하고 repository 결과를 그대로 반환한다")
    void hasBlockingOrders_queriesBlockingStatuses() {
        OrderMemberWithdrawalGuard guard = new OrderMemberWithdrawalGuard(orderRepository);
        when(orderRepository.existsByMemberIdAndStatusIn(eq(1L), any())).thenReturn(true);

        boolean result = guard.hasBlockingOrders(1L);

        assertThat(result).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<OrderStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).existsByMemberIdAndStatusIn(eq(1L), captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("진행 중 주문이 없으면 false 를 반환한다")
    void hasBlockingOrders_noneBlocking_returnsFalse() {
        OrderMemberWithdrawalGuard guard = new OrderMemberWithdrawalGuard(orderRepository);
        when(orderRepository.existsByMemberIdAndStatusIn(eq(1L), any())).thenReturn(false);

        assertThat(guard.hasBlockingOrders(1L)).isFalse();
    }
}
