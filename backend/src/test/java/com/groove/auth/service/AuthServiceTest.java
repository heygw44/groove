package com.groove.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import com.groove.auth.jwt.RefreshTokenClaims;
import com.groove.auth.repository.RefreshRotation;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.auth.repository.RotationResult;
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
	private static final String SESSION_ID = "session-1";

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	JwtProvider jwtProvider;

	JwtProperties jwtProperties = new JwtProperties(
			"test-secret-key-for-jwt-signing-must-be-long-enough-000000", Duration.ofMinutes(30), Duration.ofDays(14),
			Duration.ofSeconds(10));

	Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));

	AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(memberRepository, passwordEncoder, refreshTokenRepository, jwtProvider,
				jwtProperties, clock);
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
			verify(refreshTokenRepository, never()).save(anyLong(), anyString(), anyString());
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
			verify(refreshTokenRepository, never()).save(anyLong(), anyString(), anyString());
		}

		@Test
		@DisplayName("인증에 성공하면 새 세션 id 로 토큰을 발급하고 저장한다")
		void issuesTokensAndSavesRefresh() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("access");
			given(jwtProvider.createRefreshToken(eq(MEMBER_ID), anyString())).willReturn("refresh");

			// when
			AuthTokens tokens = authService.login(request);

			// then
			verify(refreshTokenRepository).save(eq(MEMBER_ID), anyString(), eq("refresh"));
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
		@DisplayName("세션이 없으면 AUTH_REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
		void throwsNotFoundWhenSessionMissing() {
			// given
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(MEMBER_ID, SESSION_ID, "refresh", "new-refresh", clock.millis()))
					.willReturn(new RefreshRotation(RotationResult.NOT_FOUND, null));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
		}

		@Test
		@DisplayName("재사용으로 판정되면 AUTH_REFRESH_TOKEN_MISMATCH 예외를 던진다")
		void throwsMismatchWhenReused() {
			// given
			given(jwtProvider.parseRefreshToken("old-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(MEMBER_ID, SESSION_ID, "old-refresh", "new-refresh", clock.millis()))
					.willReturn(new RefreshRotation(RotationResult.REUSED, null));

			// when & then
			assertThatThrownBy(() -> authService.reissue("old-refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
			verify(memberRepository, never()).findById(any());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(MEMBER_ID, SESSION_ID, "refresh", "new-refresh", clock.millis()))
					.willReturn(new RefreshRotation(RotationResult.ROTATED, "new-refresh"));
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
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(MEMBER_ID, SESSION_ID, "refresh", "new-refresh", clock.millis()))
					.willReturn(new RefreshRotation(RotationResult.ROTATED, "new-refresh"));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_MEMBER_SUSPENDED);
		}

		@Test
		@DisplayName("Lua 가 ROTATED 를 반환하면 새 refresh token 을 응답한다")
		void returnsRotatedRefreshToken() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("old-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(MEMBER_ID, SESSION_ID, "old-refresh", "new-refresh", clock.millis()))
					.willReturn(new RefreshRotation(RotationResult.ROTATED, "new-refresh"));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("new-access");

			// when
			AuthTokens tokens = authService.reissue("old-refresh");

			// then
			assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
			assertThat(tokens.accessToken()).isEqualTo("new-access");
		}

		@Test
		@DisplayName("Lua 가 GRACE 를 반환하면 새로 발급하지 않고 현재 토큰을 그대로 응답한다")
		void returnsCurrentRefreshTokenOnGrace() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("prev-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("attempted-new");
			given(refreshTokenRepository.rotate(MEMBER_ID, SESSION_ID, "prev-refresh", "attempted-new", clock.millis()))
					.willReturn(new RefreshRotation(RotationResult.GRACE, "current-refresh"));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("new-access");

			// when
			AuthTokens tokens = authService.reissue("prev-refresh");

			// then
			assertThat(tokens.refreshToken()).isEqualTo("current-refresh");
		}
	}

	@Nested
	@DisplayName("logout()")
	class Logout {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("refresh token 이 비어있으면 아무 것도 하지 않는다")
		void doesNothingWhenTokenBlank(String refreshToken) {
			// when
			authService.logout(MEMBER_ID, refreshToken);

			// then
			verify(refreshTokenRepository, never()).delete(any(), any());
		}

		@Test
		@DisplayName("파싱한 memberId 가 요청자와 같으면 해당 세션을 삭제한다")
		void deletesSessionWhenSubjectMatches() {
			// given
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));

			// when
			authService.logout(MEMBER_ID, "refresh");

			// then
			verify(refreshTokenRepository).delete(MEMBER_ID, SESSION_ID);
		}

		@Test
		@DisplayName("파싱한 memberId 가 요청자와 다르면 삭제하지 않는다")
		void doesNotDeleteWhenSubjectMismatches() {
			// given
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(999L, SESSION_ID));

			// when
			authService.logout(MEMBER_ID, "refresh");

			// then
			verify(refreshTokenRepository, never()).delete(any(), any());
		}

		@Test
		@DisplayName("토큰 파싱이 실패해도 예외를 삼키고 조용히 넘어간다")
		void swallowsExceptionWhenTokenInvalid() {
			// given
			given(jwtProvider.parseRefreshToken("expired"))
					.willThrow(new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN));

			// when & then
			authService.logout(MEMBER_ID, "expired");
			verify(refreshTokenRepository, never()).delete(any(), any());
		}
	}
}
