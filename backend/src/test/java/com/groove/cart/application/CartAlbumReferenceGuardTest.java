package com.groove.cart.application;

import com.groove.cart.domain.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartAlbumReferenceGuard 단위 테스트")
class CartAlbumReferenceGuardTest {

    @Mock
    private CartRepository cartRepository;

    @Test
    @DisplayName("장바구니 항목이 앨범을 참조하면 isReferenced=true (repository 결과 위임)")
    void isReferenced_delegatesToRepository() {
        CartAlbumReferenceGuard guard = new CartAlbumReferenceGuard(cartRepository);
        when(cartRepository.existsByAlbumId(10L)).thenReturn(true);

        assertThat(guard.isReferenced(10L)).isTrue();
    }

    @Test
    @DisplayName("참조하는 장바구니 항목이 없으면 isReferenced=false")
    void isReferenced_noReference_returnsFalse() {
        CartAlbumReferenceGuard guard = new CartAlbumReferenceGuard(cartRepository);
        when(cartRepository.existsByAlbumId(10L)).thenReturn(false);

        assertThat(guard.isReferenced(10L)).isFalse();
    }
}
