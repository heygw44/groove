package com.groove.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.groove.global.config.AuthSessionProperties;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final String SESSION_ID = "session-1";
	private static final String MEMBER_LOCK_KEY = "login-fail:m:1";

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	JwtProvider jwtProvider;

	@Mock
	LoginAttemptGuard loginAttemptGuard;

	JwtProperties jwtProperties = new JwtProperties(
			"test-secret-key-for-jwt-signing-must-be-long-enough-000000", Duration.ofMinutes(30), Duration.ofDays(14),
			Duration.ofSeconds(10), Duration.ofMinutes(5));

	AuthSessionProperties sessionProperties = new AuthSessionProperties(Duration.ofDays(30), Duration.ofHours(12),
			Duration.ofMinutes(20));

	Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));

	AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(memberRepository, passwordEncoder, refreshTokenRepository, jwtProvider,
				jwtProperties, sessionProperties, clock, loginAttemptGuard);
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
		@DisplayName("잠겨 있으면 AUTH_LOGIN_LOCKED 예외를 던지고 인증을 시도하지 않는다")
		void throwsWhenLocked() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			willThrow(new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED))
					.given(loginAttemptGuard).checkNotLocked(MEMBER_LOCK_KEY);

			// when & then
			assertThatThrownBy(() -> authService.login(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
			verify(passwordEncoder, never()).matches(any(), any());
		}

		@Test
		@DisplayName("이메일이 존재하지 않으면 AUTH_INVALID_CREDENTIALS 예외를 던지고 실패를 기록한다")
		void throwsWhenEmailNotFound() {
			// given
			LoginRequest request = new LoginRequest("groover@groove.com", "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.login(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
			// 존재하지 않는 이메일도 더미 해시로 BCrypt 를 한 번 태워 타이밍으로 이메일 존재 여부가 드러나지 않게 한다.
			verify(passwordEncoder).matches(eq(request.password()), isNull());
			verify(loginAttemptGuard).recordFailure("login-fail:e:groover@groove.com");
		}

		@Test
		@DisplayName("이메일이 존재하지 않으면 전각·대소문자 변형도 NFKC 로 정규화한 같은 키로 센다")
		void usesNormalizedEmailKeyForUnknownEmailVariants() {
			// given
			LoginRequest fullwidth = new LoginRequest(" ＧＲＯＯＶＥＲ@groove.com ", "password1");
			LoginRequest upper = new LoginRequest("GROOVER@Groove.com", "password1");
			given(memberRepository.findByEmail(any())).willReturn(Optional.empty());

			// when
			assertThatThrownBy(() -> authService.login(fullwidth)).isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> authService.login(upper)).isInstanceOf(BusinessException.class);

			// then
			verify(loginAttemptGuard, times(2)).checkNotLocked("login-fail:e:groover@groove.com");
			verify(loginAttemptGuard, times(2)).recordFailure("login-fail:e:groover@groove.com");
		}

		@Test
		@DisplayName("존재하는 회원이면 이메일 표기가 달라도 회원 id 키 하나로 센다")
		void usesMemberKeyRegardlessOfEmailVariant() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			LoginRequest variant = new LoginRequest("ＧＲＯＯＶＥＲ@groove.com", "wrong-password");
			given(memberRepository.findByEmail(variant.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(variant.password(), member.getPassword())).willReturn(false);

			// when
			assertThatThrownBy(() -> authService.login(variant)).isInstanceOf(BusinessException.class);

			// then
			verify(loginAttemptGuard).checkNotLocked(MEMBER_LOCK_KEY);
			verify(loginAttemptGuard).recordFailure(MEMBER_LOCK_KEY);
		}

		@Test
		@DisplayName("비밀번호가 일치하지 않으면 AUTH_INVALID_CREDENTIALS 예외를 던지고 실패를 기록한다")
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
			verify(refreshTokenRepository, never())
					.save(anyLong(), anyString(), anyString(), anyLong(), anyLong(), any());
			verify(loginAttemptGuard).recordFailure(MEMBER_LOCK_KEY);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던지고 실패로 기록하지 않는다")
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
			verify(loginAttemptGuard, never()).recordFailure(any());
			verify(loginAttemptGuard, never()).reset(any());
		}

		@Test
		@DisplayName("정지된 회원이면 AUTH_MEMBER_SUSPENDED 예외를 던지고 실패로 기록하지 않는다")
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
			verify(refreshTokenRepository, never())
					.save(anyLong(), anyString(), anyString(), anyLong(), anyLong(), any());
			verify(loginAttemptGuard, never()).recordFailure(any());
			verify(loginAttemptGuard, never()).reset(any());
		}

		@Test
		@DisplayName("인증에 성공하면 실패 카운터를 초기화하고 새 세션 id 로 토큰을 발급해 저장한다")
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
			verify(refreshTokenRepository)
					.save(eq(MEMBER_ID), anyString(), eq("refresh"), anyLong(), anyLong(), any());
			verify(loginAttemptGuard).reset(MEMBER_LOCK_KEY);
			assertThat(tokens.accessToken()).isEqualTo("access");
			assertThat(tokens.expiresIn()).isEqualTo(1800L);
		}

		@Test
		@DisplayName("USER 는 절대 만료 30일로 저장하고 maxAge 는 refreshTokenExpiry(14일) 로 캡된다")
		void savesUserAbsoluteExpiryAndCapsMaxAgeAtRefreshTokenExpiry() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			LoginRequest request = new LoginRequest(member.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(member));
			given(passwordEncoder.matches(request.password(), member.getPassword())).willReturn(true);
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("access");
			given(jwtProvider.createRefreshToken(eq(MEMBER_ID), anyString())).willReturn("refresh");
			long now = clock.millis();
			long expectedAbsExp = now + Duration.ofDays(30).toMillis();

			// when
			AuthTokens tokens = authService.login(request);

			// then
			verify(refreshTokenRepository)
					.save(eq(MEMBER_ID), anyString(), eq("refresh"), eq(expectedAbsExp), eq(now), any());
			assertThat(tokens.refreshTokenMaxAge()).isEqualTo(jwtProperties.refreshTokenExpiry());
		}

		@Test
		@DisplayName("ADMIN 은 절대 만료 12시간으로 저장하고 maxAge 도 12시간이다")
		void savesAdminAbsoluteExpiryAndUsesItAsMaxAge() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.createAdmin(), MEMBER_ID);
			LoginRequest request = new LoginRequest(admin.getEmail(), "password1");
			given(memberRepository.findByEmail(request.email())).willReturn(Optional.of(admin));
			given(passwordEncoder.matches(request.password(), admin.getPassword())).willReturn(true);
			given(jwtProvider.createAccessToken(MEMBER_ID, admin.getRole())).willReturn("access");
			given(jwtProvider.createRefreshToken(eq(MEMBER_ID), anyString())).willReturn("refresh");
			long now = clock.millis();
			long expectedAbsExp = now + Duration.ofHours(12).toMillis();

			// when
			AuthTokens tokens = authService.login(request);

			// then
			verify(refreshTokenRepository)
					.save(eq(MEMBER_ID), anyString(), eq("refresh"), eq(expectedAbsExp), eq(now), eq(MemberRole.ADMIN));
			assertThat(tokens.refreshTokenMaxAge()).isEqualTo(Duration.ofHours(12));
			assertThat(tokens.expiresIn()).isEqualTo(Duration.ofMinutes(5).toSeconds());
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
		@DisplayName("회원을 찾을 수 없으면 MEMBER_NOT_FOUND 예외를 던지고 rotate 를 부르지 않는다")
		void throwsMemberNotFoundAndNeverRotates() {
			// given
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
			verify(refreshTokenRepository, never()).rotate(any(), any(), any(), any(), anyLong(), anyLong(), any(),
					any());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던지고 rotate 를 부르지 않는다")
		void throwsWhenMemberWithdrawnAndNeverRotates() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
			verify(refreshTokenRepository, never()).rotate(any(), any(), any(), any(), anyLong(), anyLong(), any(),
					any());
		}

		@Test
		@DisplayName("정지된 회원이면 AUTH_MEMBER_SUSPENDED 예외를 던지고 rotate 를 부르지 않는다")
		void throwsWhenMemberSuspendedAndNeverRotates() {
			// given
			Member member = MemberFixture.withId(MemberFixture.createSuspended(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_MEMBER_SUSPENDED);
			verify(refreshTokenRepository, never()).rotate(any(), any(), any(), any(), anyLong(), anyLong(), any(),
					any());
		}

		@Test
		@DisplayName("세션이 없으면 AUTH_REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
		void throwsNotFoundWhenSessionMissing() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("refresh"), eq("new-refresh"),
					eq(clock.millis()), anyLong(), any(), any()))
					.willReturn(new RefreshRotation(RotationResult.NOT_FOUND, null, 0L));

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
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("old-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("old-refresh"), eq("new-refresh"),
					eq(clock.millis()), anyLong(), any(), any()))
					.willReturn(new RefreshRotation(RotationResult.REUSED, null, 0L));

			// when & then
			assertThatThrownBy(() -> authService.reissue("old-refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
		}

		@Test
		@DisplayName("Lua 가 EXPIRED 를 반환하면 AUTH_SESSION_EXPIRED 예외를 던진다")
		void throwsSessionExpiredWhenRotationExpired() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("old-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("old-refresh"), eq("new-refresh"),
					eq(clock.millis()), anyLong(), any(), any()))
					.willReturn(new RefreshRotation(RotationResult.EXPIRED, null, 0L));

			// when & then
			assertThatThrownBy(() -> authService.reissue("old-refresh"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_SESSION_EXPIRED);
		}

		@Test
		@DisplayName("Lua 가 ROTATED 를 반환하면 새 refresh token 과 캡 적용된 maxAge 를 응답한다")
		void returnsRotatedRefreshTokenWithCappedMaxAge() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("old-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			long now = clock.millis();
			long absoluteExpiresAt = now + Duration.ofDays(20).toMillis();
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("old-refresh"), eq("new-refresh"),
					eq(now), anyLong(), any(), any()))
					.willReturn(new RefreshRotation(RotationResult.ROTATED, "new-refresh", absoluteExpiresAt));
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("new-access");

			// when
			AuthTokens tokens = authService.reissue("old-refresh");

			// then
			assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
			assertThat(tokens.accessToken()).isEqualTo("new-access");
			assertThat(tokens.refreshTokenMaxAge()).isEqualTo(jwtProperties.refreshTokenExpiry());
		}

		@Test
		@DisplayName("관리자의 클라이언트 무입력 시간이 유휴 만료 이상이면 세션을 지우고 AUTH_SESSION_IDLE 예외를 던진다")
		void throwsSessionIdleForAdminWhenClientIdleReachesTimeout() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.createAdmin(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(admin));

			// when & then
			assertThatThrownBy(() -> authService.reissue("refresh", Duration.ofMinutes(20)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_SESSION_IDLE);
			verify(refreshTokenRepository).delete(MEMBER_ID, SESSION_ID);
			verify(refreshTokenRepository, never()).rotate(any(), any(), any(), any(), anyLong(), anyLong(), any(),
					any());
		}

		@Test
		@DisplayName("관리자의 클라이언트 무입력 시간이 유휴 만료 미만이면 그 값을 넘겨 회전한다")
		void rotatesWithClientIdleForAdminBelowTimeout() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.createAdmin(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(admin));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			long now = clock.millis();
			Duration clientIdle = Duration.ofMinutes(19);
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("refresh"), eq("new-refresh"),
					eq(now), anyLong(), eq(MemberRole.ADMIN), eq(clientIdle)))
					.willReturn(new RefreshRotation(RotationResult.ROTATED, "new-refresh",
							now + Duration.ofHours(1).toMillis()));
			given(jwtProvider.createAccessToken(MEMBER_ID, admin.getRole())).willReturn("new-access");

			// when
			AuthTokens tokens = authService.reissue("refresh", clientIdle);

			// then
			assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
			verify(refreshTokenRepository, never()).delete(any(), any());
		}

		@Test
		@DisplayName("일반 회원은 클라이언트 무입력 시간이 아무리 커도 정상 회전한다")
		void rotatesUserRegardlessOfClientIdle() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("refresh")).willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("new-refresh");
			long now = clock.millis();
			Duration clientIdle = Duration.ofDays(1);
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("refresh"), eq("new-refresh"),
					eq(now), anyLong(), eq(MemberRole.USER), eq(clientIdle)))
					.willReturn(new RefreshRotation(RotationResult.ROTATED, "new-refresh",
							now + Duration.ofDays(1).toMillis()));
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("new-access");

			// when
			AuthTokens tokens = authService.reissue("refresh", clientIdle);

			// then
			assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
			verify(refreshTokenRepository, never()).delete(any(), any());
		}

		@Test
		@DisplayName("Lua 가 GRACE 를 반환하면 새로 발급하지 않고 현재 토큰을 그대로 응답한다")
		void returnsCurrentRefreshTokenOnGrace() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
			given(jwtProvider.parseRefreshToken("prev-refresh"))
					.willReturn(new RefreshTokenClaims(MEMBER_ID, SESSION_ID));
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID)).willReturn("attempted-new");
			long now = clock.millis();
			long absoluteExpiresAt = now + Duration.ofDays(1).toMillis();
			given(refreshTokenRepository.rotate(eq(MEMBER_ID), eq(SESSION_ID), eq("prev-refresh"),
					eq("attempted-new"), eq(now), anyLong(), any(), any()))
					.willReturn(new RefreshRotation(RotationResult.GRACE, "current-refresh", absoluteExpiresAt));
			given(jwtProvider.createAccessToken(MEMBER_ID, member.getRole())).willReturn("new-access");

			// when
			AuthTokens tokens = authService.reissue("prev-refresh");

			// then
			assertThat(tokens.refreshToken()).isEqualTo("current-refresh");
			assertThat(tokens.refreshTokenMaxAge()).isEqualTo(Duration.ofDays(1));
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
