package com.groove.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.auth.dto.AuthTokens;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.auth.jwt.JwtProvider;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.fixture.MemberFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	JwtProvider jwtProvider;

	JwtProperties jwtProperties = new JwtProperties(
			"test-secret-key-for-jwt-signing-must-be-long-enough-000000", Duration.ofMinutes(30), Duration.ofDays(14));

	AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(memberRepository, passwordEncoder, refreshTokenRepository, jwtProvider,
				jwtProperties);
	}

	@Nested
	@DisplayName("signup()")
	class Signup {

		@Test
		@DisplayName("이메일이 중복되지 않으면 비밀번호를 인코딩해 저장하고 응답을 반환한다")
		void savesEncodedPasswordAndReturnsResponse() {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			given(memberRepository.existsByEmail(request.email())).willReturn(false);
			given(passwordEncoder.encode(request.password())).willReturn("encoded");
			willAnswer(invocation -> MemberFixture.withId(invocation.getArgument(0), 1L))
					.given(memberRepository).save(any(Member.class));

			// when
			SignupResponse response = authService.signup(request);

			// then
			ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
			verify(memberRepository).save(captor.capture());
			Member savedMember = captor.getValue();
			assertThat(savedMember.getPassword()).isEqualTo("encoded");
			assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
			assertThat(savedMember.getRole()).isEqualTo(MemberRole.USER);

			assertThat(response.id()).isEqualTo(1L);
			assertThat(response.email()).isEqualTo(request.email());
			assertThat(response.nickname()).isEqualTo(request.nickname());
		}

		@Test
		@DisplayName("이메일이 중복되면 MEMBER_EMAIL_DUPLICATE 예외를 던진다")
		void throwsWhenEmailDuplicated() {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			given(memberRepository.existsByEmail(request.email())).willReturn(true);

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_EMAIL_DUPLICATE);
			verify(memberRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("login()")
	class Login {

		@Test
		@DisplayName("이메일이 존재하지 않으면 AUTH_INVALID_CREDENTIALS 예외를 던진다")
		void throwsWhenEmailNotFound() {
			// given
			LoginRequest request = new LoginRequest("groover@groove.com", "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.login(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}

		@Test
		@DisplayName("비밀번호가 일치하지 않으면 AUTH_INVALID_CREDENTIALS 예외를 던진다")
		void throwsWhenPasswordMismatch() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "wrong-password");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(false);

			// when & then
			assertThatThrownBy(() -> authService.login(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
			verify(refreshTokenRepository, never()).save(anyLong(), any());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);

			// when & then
			assertThatThrownBy(() -> authService.login(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}

		@Test
		@DisplayName("정지된 회원이면 AUTH_MEMBER_SUSPENDED 예외를 던진다")
		void throwsWhenMemberSuspended() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createSuspended(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);

			// when & then
			assertThatThrownBy(() -> authService.login(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_MEMBER_SUSPENDED);
			verify(refreshTokenRepository, never()).save(anyLong(), any());
		}

		@Test
		@DisplayName("인증에 성공하면 토큰을 발급하고 refresh token 을 저장한다")
		void issuesTokensAndSavesRefresh() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("access");
			given(jwtProvider.createRefreshToken(MEMBER_ID)).willReturn("refresh");

			// when
			AuthTokens tokens = authService.login(request);

			// then
			verify(refreshTokenRepository).save(1L, "refresh");
			assertThat(tokens.accessToken()).isEqualTo("access");
			assertThat(tokens.expiresIn()).isEqualTo(1800L);
		}
	}

	@Nested
	@DisplayName("reissue()")
	class Reissue {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("refresh token 이 비어있으면 AUTH_REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
		void throwsNotFoundWhenTokenBlank(String refreshToken) {
			// when & then
			assertThatThrownBy(() -> authService.reissue(refreshToken))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
		}

		@Test
		@DisplayName("Redis 에 저장된 토큰이 없으면 AUTH_REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
		void throwsNotFoundWhenRedisHasNoToken() {
			// given
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(MEMBER_ID);
			given(refreshTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
		}

		@Test
		@DisplayName("저장된 토큰과 다르면 세션을 삭제하고 AUTH_REFRESH_TOKEN_MISMATCH 예외를 던진다")
		void deletesSessionAndThrowsMismatchWhenTokenDiffers() {
			// given
			given(jwtProvider.parseRefreshToken("old-refresh")).willReturn(MEMBER_ID);
			given(refreshTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of("new-refresh"));

			// when & then
			assertThatThrownBy(() -> authService.reissue("old-refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
			verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(MEMBER_ID);
			given(refreshTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of("refresh"));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
		}

		@Test
		@DisplayName("정지된 회원이면 AUTH_MEMBER_SUSPENDED 예외를 던진다")
		void throwsWhenMemberSuspended() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createSuspended(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(MEMBER_ID);
			given(refreshTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of("refresh"));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_MEMBER_SUSPENDED);
		}

		@Test
		@DisplayName("검증에 성공하면 새 refresh token 으로 회전한다")
		void rotatesRefreshToken() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("old-refresh")).willReturn(MEMBER_ID);
			given(refreshTokenRepository.findByMemberId(MEMBER_ID)).willReturn(Optional.of("old-refresh"));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("new-access");
			given(jwtProvider.createRefreshToken(MEMBER_ID)).willReturn("new-refresh");

			// when
			AuthTokens tokens = authService.reissue("old-refresh");

			// then
			verify(refreshTokenRepository).save(MEMBER_ID, "new-refresh");
			assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
		}
	}

	@Nested
	@DisplayName("logout()")
	class Logout {

		@Test
		@DisplayName("refresh token 을 삭제한다")
		void deletesRefreshToken() {
			// when
			authService.logout(MEMBER_ID);

			// then
			verify(refreshTokenRepository).deleteByMemberId(MEMBER_ID);
		}
	}
}
