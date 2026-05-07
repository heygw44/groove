package com.groove.auth.application;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService 단위 테스트.
 *
 * <p>로그인 성공·실패·사용자 열거 방지 동작을 검증한다.
 * JwtProvider/PasswordEncoder/MemberRepository 는 모두 모킹.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "Password!1234";
    private static final String PASSWORD_HASH = "$2a$10$hash";
    private static final long ACCESS_TTL = 1800L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.register(EMAIL, PASSWORD_HASH, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Test
    @DisplayName("정상 로그인: access·refresh 토큰을 발급해 TokenPair 로 반환한다")
    void login_success() {
        given(memberRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(jwtProvider.issueAccessToken(1L, MemberRole.USER)).willReturn("access-token");
        given(jwtProvider.issueRefreshToken(1L)).willReturn("refresh-token");
        given(jwtProperties.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);

        TokenPair result = authService.login(new LoginCommand(EMAIL, RAW_PASSWORD));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessTokenExpiresInSeconds()).isEqualTo(ACCESS_TTL);
    }

    @Test
    @DisplayName("이메일 미존재: AUTH_INVALID_CREDENTIALS 로 실패한다 (사용자 열거 방지)")
    void login_emailNotFound_throwsInvalidCredentials() {
        given(memberRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtProvider, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("비밀번호 불일치: 미존재와 동일한 AUTH_INVALID_CREDENTIALS 로 실패한다")
    void login_wrongPassword_throwsInvalidCredentials() {
        given(memberRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);

        verify(jwtProvider, never()).issueAccessToken(any(), any());
        verify(jwtProvider, never()).issueRefreshToken(any());
    }

    @Test
    @DisplayName("토큰 발급 시 회원 ID·Role 이 정확히 전달된다")
    void login_passesMemberIdAndRoleToJwtProvider() {
        given(memberRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(jwtProvider.issueAccessToken(eq(1L), eq(MemberRole.USER))).willReturn("access");
        given(jwtProvider.issueRefreshToken(eq(1L))).willReturn("refresh");
        given(jwtProperties.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);

        authService.login(new LoginCommand(EMAIL, RAW_PASSWORD));

        verify(jwtProvider).issueAccessToken(1L, MemberRole.USER);
        verify(jwtProvider).issueRefreshToken(1L);
    }
}
