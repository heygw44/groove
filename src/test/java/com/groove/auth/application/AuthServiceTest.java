package com.groove.auth.application;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AuthService 단위 테스트.
 *
 * <p>로그인 성공·실패·사용자 열거 방지 동작을 검증한다.
 * 토큰 발급 책임은 {@link RefreshTokenService} 가 가지므로 모킹 후 호출만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "Password!1234";
    private static final String PASSWORD_HASH = "$2a$10$hash";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.register(EMAIL, PASSWORD_HASH, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    @Test
    @DisplayName("정상 로그인: RefreshTokenService.issueOnLogin 위임 결과를 그대로 반환한다")
    void login_success() {
        TokenPair issued = new TokenPair("access-token", "refresh-token", 1800L);
        given(memberRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(member));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(refreshTokenService.issueOnLogin(member)).willReturn(issued);

        TokenPair result = authService.login(new LoginCommand(EMAIL, RAW_PASSWORD));

        assertThat(result).isSameAs(issued);
        verify(refreshTokenService).issueOnLogin(member);
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
        verify(refreshTokenService, never()).issueOnLogin(any());
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

        verify(refreshTokenService, never()).issueOnLogin(any());
    }
}
