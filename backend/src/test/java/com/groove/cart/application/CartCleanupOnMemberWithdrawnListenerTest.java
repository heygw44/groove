package com.groove.cart.application;

import com.groove.member.event.MemberWithdrawnEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartCleanupOnMemberWithdrawnListener 단위 테스트")
class CartCleanupOnMemberWithdrawnListenerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartCleanupOnMemberWithdrawnListener listener;

    @Test
    @DisplayName("탈퇴 이벤트 수신 → 해당 회원 장바구니 삭제 위임")
    void onMemberWithdrawn_delegatesDelete() {
        listener.onMemberWithdrawn(new MemberWithdrawnEvent(42L));

        verify(cartService).deleteForMember(42L);
    }

    @Test
    @DisplayName("장바구니 삭제 실패는 흡수 — 예외가 호출자로 전파되지 않음(탈퇴는 이미 확정)")
    void onMemberWithdrawn_swallowsException() {
        doThrow(new RuntimeException("DB down")).when(cartService).deleteForMember(42L);

        assertThatCode(() -> listener.onMemberWithdrawn(new MemberWithdrawnEvent(42L)))
                .doesNotThrowAnyException();
    }
}
