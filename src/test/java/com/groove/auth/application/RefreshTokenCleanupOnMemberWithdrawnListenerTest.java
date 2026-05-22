package com.groove.auth.application;

import com.groove.member.event.MemberWithdrawnEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenCleanupOnMemberWithdrawnListener 단위 테스트")
class RefreshTokenCleanupOnMemberWithdrawnListenerTest {

    private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");

    @Mock
    private RefreshTokenAdmin refreshTokenAdmin;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private RefreshTokenCleanupOnMemberWithdrawnListener listener;

    @Test
    @DisplayName("탈퇴 이벤트 수신 → 해당 회원 활성 토큰 일괄 revoke 위임")
    void onMemberWithdrawn_revokesAllActiveSessions() {
        listener = new RefreshTokenCleanupOnMemberWithdrawnListener(refreshTokenAdmin, clock);
        when(refreshTokenAdmin.forceRevokeAllActiveSessions(eq(42L), eq(NOW))).thenReturn(2);

        listener.onMemberWithdrawn(new MemberWithdrawnEvent(42L));

        verify(refreshTokenAdmin).forceRevokeAllActiveSessions(42L, NOW);
    }

    @Test
    @DisplayName("revoke 실패는 흡수 — 예외가 호출자로 전파되지 않음(탈퇴는 이미 확정)")
    void onMemberWithdrawn_swallowsException() {
        listener = new RefreshTokenCleanupOnMemberWithdrawnListener(refreshTokenAdmin, clock);
        doThrow(new RuntimeException("DB down"))
                .when(refreshTokenAdmin).forceRevokeAllActiveSessions(eq(42L), eq(NOW));

        assertThatCode(() -> listener.onMemberWithdrawn(new MemberWithdrawnEvent(42L)))
                .doesNotThrowAnyException();
    }
}
