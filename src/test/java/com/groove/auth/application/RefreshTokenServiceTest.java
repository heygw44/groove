package com.groove.auth.application;

import com.groove.auth.domain.RefreshToken;
import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.domain.TokenHasher;
import com.groove.auth.security.JwtClaims;
import com.groove.auth.security.JwtProperties;
import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RefreshTokenService 단위 테스트.
 *
 * <p>의존 컴포넌트는 모두 모킹. 시간은 {@link Clock#fixed} 로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long ACCESS_TTL = 1_800L;
    private static final long REFRESH_TTL = 1_209_600L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Long MEMBER_ID = 1L;
    private static final String RAW_REFRESH = "valid.jwt.payload";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenAdmin refreshTokenAdmin;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private JwtProperties jwtProperties;

    private RefreshTokenService service;
    private Member member;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RefreshTokenService(
                refreshTokenRepository, refreshTokenAdmin, memberRepository, jwtProvider, jwtProperties, clock);
        member = Member.register("user@example.com", "$2a$12$hash", "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
    }

    // ────────────────────── issueOnLogin ──────────────────────

    @Test
    @DisplayName("issueOnLogin: access·refresh 발급 + 해시 영속화 + TokenPair 반환")
    void issueOnLogin_persistsHashedRefreshAndReturnsPair() {
        given(jwtProvider.issueAccessToken(MEMBER_ID, MemberRole.USER)).willReturn("access");
        given(jwtProvider.issueRefreshToken(MEMBER_ID)).willReturn("refresh");
        given(jwtProperties.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);
        given(jwtProperties.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);

        TokenPair tokens = service.issueOnLogin(member);

        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("refresh");
        assertThat(tokens.accessTokenExpiresInSeconds()).isEqualTo(ACCESS_TTL);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex("refresh"));
        assertThat(saved.getIssuedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(REFRESH_TTL));
    }

    // ────────────────────── rotate (정상) ──────────────────────

    @Test
    @DisplayName("rotate 정상: 새 access·refresh 발급 + 기존 토큰 conditional revoke + 응답에 둘 다 포함")
    void rotate_success_returnsNewPairAndRevokesOldAtomic() {
        RefreshToken existing = persistedActive(MEMBER_ID, RAW_REFRESH, 100L);
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(RAW_REFRESH)))
                .willReturn(Optional.of(existing));
        given(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(Optional.of(member));
        given(jwtProvider.issueAccessToken(MEMBER_ID, MemberRole.USER)).willReturn("new-access");
        given(jwtProvider.issueRefreshToken(MEMBER_ID)).willReturn("new-refresh");
        given(jwtProperties.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);
        given(jwtProperties.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);
        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willAnswer(inv -> {
                    RefreshToken row = inv.getArgument(0);
                    ReflectionTestUtils.setField(row, "id", 200L);
                    return row;
                });
        given(refreshTokenRepository.revokeIfActive(eq(100L), any(Instant.class), eq(200L)))
                .willReturn(1);

        TokenPair result = service.rotate(RAW_REFRESH);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).revokeIfActive(100L, NOW, 200L);
        verify(refreshTokenAdmin, never()).forceRevokeAllActiveSessions(anyLong(), any());
    }

    // ────────────────────── rotate (실패) ──────────────────────

    @Test
    @DisplayName("rotate: 알 수 없는 토큰 → AUTH_INVALID_TOKEN")
    void rotate_unknownTokenHash_throwsInvalid() {
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(RAW_REFRESH))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate: 이미 revoked 토큰 재사용 → 같은 사용자 활성 토큰 전체 무효화 + AUTH_INVALID_TOKEN")
    void rotate_reusedRevokedToken_revokesAllAndThrows() {
        RefreshToken revoked = persistedActive(MEMBER_ID, RAW_REFRESH, 100L);
        ReflectionTestUtils.setField(revoked, "revokedAt", NOW.minusSeconds(60));

        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate(RAW_REFRESH))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        verify(refreshTokenAdmin).forceRevokeAllActiveSessions(MEMBER_ID, NOW);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate: DB 만료 토큰 → AUTH_EXPIRED_TOKEN")
    void rotate_dbExpired_throwsExpired() {
        RefreshToken expired = RefreshToken.issue(
                MEMBER_ID,
                TokenHasher.sha256Hex(RAW_REFRESH),
                NOW.minusSeconds(REFRESH_TTL + 60),
                NOW.minusSeconds(60)
        );
        ReflectionTestUtils.setField(expired, "id", 100L);

        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(RAW_REFRESH))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);

        verify(refreshTokenAdmin, never()).forceRevokeAllActiveSessions(anyLong(), any());
    }

    @Test
    @DisplayName("rotate: 비활성(soft-deleted) 회원 → AUTH_INVALID_TOKEN")
    void rotate_deletedMember_throwsInvalid() {
        RefreshToken existing = persistedActive(MEMBER_ID, RAW_REFRESH, 100L);
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(existing));
        given(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(RAW_REFRESH))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate: 동시 회전 race 패배(revokeIfActive 0행) → 단순 거부, 전체 무효화 없음")
    void rotate_raceLost_throwsWithoutFullRevoke() {
        RefreshToken existing = persistedActive(MEMBER_ID, RAW_REFRESH, 100L);
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(existing));
        given(memberRepository.findByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(Optional.of(member));
        given(jwtProvider.issueAccessToken(MEMBER_ID, MemberRole.USER)).willReturn("new-access");
        given(jwtProvider.issueRefreshToken(MEMBER_ID)).willReturn("new-refresh");
        given(jwtProperties.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);
        given(refreshTokenRepository.save(any(RefreshToken.class)))
                .willAnswer(inv -> {
                    RefreshToken row = inv.getArgument(0);
                    ReflectionTestUtils.setField(row, "id", 201L);
                    return row;
                });
        given(refreshTokenRepository.revokeIfActive(eq(100L), any(Instant.class), eq(201L)))
                .willReturn(0); // race 패배

        assertThatThrownBy(() -> service.rotate(RAW_REFRESH))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

        // 정상 사용자의 동시 요청 가능성을 고려해 race 패배는 전체 무효화하지 않는다.
        // (실제 도난된 토큰의 재사용은 isRevoked()=true 분기로 잡힌다)
        verify(refreshTokenAdmin, never()).forceRevokeAllActiveSessions(anyLong(), any());
    }

    @Test
    @DisplayName("rotate: JWT 파싱 단계에서 만료 토큰 → AUTH_EXPIRED_TOKEN 그대로 전파")
    void rotate_jwtExpired_propagates() {
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willThrow(new AuthException(ErrorCode.AUTH_EXPIRED_TOKEN));

        assertThatThrownBy(() -> service.rotate(RAW_REFRESH))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
    }

    // ────────────────────── revoke ──────────────────────

    @Test
    @DisplayName("revoke: 정상 토큰 → conditional revoke 1회 호출")
    void revoke_validToken_callsRevokeIfActive() {
        RefreshToken existing = persistedActive(MEMBER_ID, RAW_REFRESH, 100L);
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(existing));

        service.revoke(RAW_REFRESH);

        verify(refreshTokenRepository, times(1)).revokeIfActive(100L, NOW, null);
    }

    @Test
    @DisplayName("revoke: 잘못된 형식/만료 토큰 → 멱등 무동작 (RFC 7009)")
    void revoke_invalidFormat_isIdempotentNoOp() {
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willThrow(new AuthException(ErrorCode.AUTH_INVALID_TOKEN));

        service.revoke(RAW_REFRESH);

        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).revokeIfActive(anyLong(), any(), any());
    }

    @Test
    @DisplayName("revoke: 이미 revoked 토큰 → 추가 revoke 호출 없음 (멱등)")
    void revoke_alreadyRevoked_doesNothing() {
        RefreshToken revoked = persistedActive(MEMBER_ID, RAW_REFRESH, 100L);
        ReflectionTestUtils.setField(revoked, "revokedAt", NOW.minusSeconds(10));
        given(jwtProvider.parseRefreshToken(RAW_REFRESH))
                .willReturn(new JwtClaims(MEMBER_ID, null));
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.of(revoked));

        service.revoke(RAW_REFRESH);

        verify(refreshTokenRepository, never()).revokeIfActive(anyLong(), any(), any());
    }

    private RefreshToken persistedActive(Long memberId, String rawToken, long id) {
        RefreshToken row = RefreshToken.issue(
                memberId,
                TokenHasher.sha256Hex(rawToken),
                NOW.minusSeconds(60),
                NOW.plusSeconds(REFRESH_TTL)
        );
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }
}
