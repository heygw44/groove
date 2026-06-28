package com.groove.order.application;

import com.groove.order.domain.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAlbumReferenceGuard 단위 테스트")
class OrderAlbumReferenceGuardTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("주문 항목이 앨범을 참조하면 isReferenced=true (repository 결과 위임)")
    void isReferenced_delegatesToRepository() {
        OrderAlbumReferenceGuard guard = new OrderAlbumReferenceGuard(orderRepository);
        when(orderRepository.existsByAlbumId(10L)).thenReturn(true);

        assertThat(guard.isReferenced(10L)).isTrue();
    }

    @Test
    @DisplayName("참조하는 주문 항목이 없으면 isReferenced=false")
    void isReferenced_noReference_returnsFalse() {
        OrderAlbumReferenceGuard guard = new OrderAlbumReferenceGuard(orderRepository);
        when(orderRepository.existsByAlbumId(10L)).thenReturn(false);

        assertThat(guard.isReferenced(10L)).isFalse();
    }
}
