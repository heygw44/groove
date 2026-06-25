package com.groove.cart.application;

import com.groove.cart.domain.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 장바구니 쓰기 오케스트레이터 단위 테스트 — CartSteps 위임과 addItem 동시성 충돌 1회 멱등 재시도.
 * 트랜잭션 단계 자체의 검증은 {@link CartStepsTest} 에 있다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService")
class CartServiceTest {

    @Mock
    private CartSteps steps;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(steps);
    }

    @Test
    @DisplayName("addItem: 충돌 없으면 steps.addItem 1회 호출하고 결과 반환")
    void addItem_noConflict_delegatesOnce() {
        Cart result = Cart.openFor(1L);
        when(steps.addItem(1L, 10L, 2)).thenReturn(result);

        Cart cart = cartService.addItem(1L, 10L, 2);

        assertThat(cart).isSameAs(result);
        verify(steps, times(1)).addItem(1L, 10L, 2);
    }

    @Test
    @DisplayName("addItem: 첫 시도가 DIVE(동시 쓰기 충돌) → 1회 재시도로 멱등 흡수, 두 번째 결과 반환")
    void addItem_firstAttemptConflict_retriesOnceAndRecovers() {
        Cart recovered = Cart.openFor(1L);
        when(steps.addItem(1L, 10L, 2))
                .thenThrow(new DataIntegrityViolationException("uk_cart_item_cart_album"))
                .thenReturn(recovered);

        Cart cart = cartService.addItem(1L, 10L, 2);

        assertThat(cart).isSameAs(recovered);
        verify(steps, times(2)).addItem(1L, 10L, 2);
    }

    @Test
    @DisplayName("addItem: 재시도도 제약 위반이면(이론상) 예외 전파 → 전역 핸들러가 409 로 매핑")
    void addItem_retryAlsoConflicts_propagates() {
        when(steps.addItem(1L, 10L, 2))
                .thenThrow(new DataIntegrityViolationException("uk_cart_member"));

        assertThatThrownBy(() -> cartService.addItem(1L, 10L, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(steps, times(2)).addItem(1L, 10L, 2);
    }

    @Test
    @DisplayName("find / changeItemQuantity / removeItem / clear / deleteForMember → steps 로 단순 위임(재시도 없음)")
    void otherOperations_delegateWithoutRetry() {
        Cart found = Cart.openFor(1L);
        Cart changed = Cart.openFor(1L);
        Cart removed = Cart.openFor(1L);
        Cart cleared = Cart.openFor(1L);
        when(steps.find(1L)).thenReturn(found);
        when(steps.changeItemQuantity(1L, 5L, 3)).thenReturn(changed);
        when(steps.removeItem(1L, 5L)).thenReturn(removed);
        when(steps.clear(1L)).thenReturn(cleared);

        assertThat(cartService.find(1L)).isSameAs(found);
        assertThat(cartService.changeItemQuantity(1L, 5L, 3)).isSameAs(changed);
        assertThat(cartService.removeItem(1L, 5L)).isSameAs(removed);
        assertThat(cartService.clear(1L)).isSameAs(cleared);
        cartService.deleteForMember(1L);

        verify(steps, times(1)).find(1L);
        verify(steps, times(1)).changeItemQuantity(1L, 5L, 3);
        verify(steps, times(1)).removeItem(1L, 5L);
        verify(steps, times(1)).clear(1L);
        verify(steps, times(1)).deleteForMember(1L);
    }
}
