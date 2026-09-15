package com.groove.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.MemberRole;

class JwtProviderTest {

	private static final String SIGNING_KEY = "test-secret-key-for-jwt-signing-must-be-long-enough-000000";
	private static final Long MEMBER_ID = 1L;
	private static final String SESSION_ID = "session-1";

	private final JwtProvider jwtProvider = new JwtProvider(
			new JwtProperties(SIGNING_KEY, Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofSeconds(10)));

	@Nested
	@DisplayName("createAccessToken()")
	class CreateAccessToken {

		@ParameterizedTest
		@EnumSource(MemberRole.class)
		@DisplayName("발급한 토큰을 파싱하면 memberId와 role이 복원된다")
		void parsedClaimsMatchIssuedValues(MemberRole role) {
			// given
			String token = jwtProvider.createAccessToken(MEMBER_ID, role);

			// when
			TokenClaims claims = jwtProvider.parseAccessToken(token);

			// then
			assertThat(claims.memberId()).isEqualTo(MEMBER_ID);
			assertThat(claims.role()).isEqualTo(role);
		}
	}

	@Nested
	@DisplayName("createRefreshToken()")
	class CreateRefreshToken {

		@Test
		@DisplayName("연속으로 호출해도 서로 다른 토큰을 발급한다")
		void issuesDifferentTokensOnConsecutiveCalls() {
			// when
			String first = jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID);
			String second = jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID);

			// then
			assertThat(first).isNotEqualTo(second);
		}
	}

	@Nested
	@DisplayName("parseAccessToken()")
	class ParseAccessToken {

		@Test
		@DisplayName("만료된 토큰이면 AUTH_EXPIRED_TOKEN 예외를 던진다")
		void throwsExpiredTokenWhenExpired() {
			// given
			JwtProvider expiredProvider = new JwtProvider(
					new JwtProperties(SIGNING_KEY, Duration.ofMillis(-1000), Duration.ofDays(14),
							Duration.ofSeconds(10)));
			String token = expiredProvider.createAccessToken(MEMBER_ID, MemberRole.USER);

			// when & then
			assertThatThrownBy(() -> expiredProvider.parseAccessToken(token))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
		}

		@Test
		@DisplayName("다른 시크릿으로 서명된 토큰이면 AUTH_INVALID_TOKEN 예외를 던진다")
		void throwsInvalidTokenWhenSignedWithOtherSecret() {
			// given
			JwtProvider otherProvider = new JwtProvider(
					new JwtProperties("other-secret-key-for-jwt-signing-must-be-long-enough-0000",
							Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofSeconds(10)));
			String token = otherProvider.createAccessToken(MEMBER_ID, MemberRole.USER);

			// when & then
			assertThatThrownBy(() -> jwtProvider.parseAccessToken(token))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
		}

		@Test
		@DisplayName("형식이 깨진 문자열이면 AUTH_INVALID_TOKEN 예외를 던진다")
		void throwsInvalidTokenWhenMalformed() {
			// when & then
			assertThatThrownBy(() -> jwtProvider.parseAccessToken("not-a-jwt"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
		}

		@Test
		@DisplayName("빈 문자열이면 AUTH_INVALID_TOKEN 예외를 던진다")
		void throwsInvalidTokenWhenBlank() {
			// when & then
			assertThatThrownBy(() -> jwtProvider.parseAccessToken(""))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
		}

		@Test
		@DisplayName("refresh 토큰을 넣으면 AUTH_INVALID_TOKEN 예외를 던진다")
		void throwsInvalidTokenWhenGivenRefreshToken() {
			// given
			String refreshToken = jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID);

			// when & then
			assertThatThrownBy(() -> jwtProvider.parseAccessToken(refreshToken))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
		}
	}

	@Nested
	@DisplayName("parseRefreshToken()")
	class ParseRefreshToken {

		@Test
		@DisplayName("정상 토큰이면 memberId와 sessionId를 반환한다")
		void returnsMemberIdAndSessionIdWhenValid() {
			// given
			String token = jwtProvider.createRefreshToken(MEMBER_ID, SESSION_ID);

			// when
			RefreshTokenClaims claims = jwtProvider.parseRefreshToken(token);

			// then
			assertThat(claims.memberId()).isEqualTo(MEMBER_ID);
			assertThat(claims.sessionId()).isEqualTo(SESSION_ID);
		}

		@Test
		@DisplayName("access 토큰을 넣으면 AUTH_INVALID_TOKEN 예외를 던진다")
		void throwsInvalidTokenWhenGivenAccessToken() {
			// given
			String accessToken = jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);

			// when & then
			assertThatThrownBy(() -> jwtProvider.parseRefreshToken(accessToken))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("sid 클레임이 없거나 비어있으면 AUTH_INVALID_TOKEN 예외를 던진다")
		void throwsInvalidTokenWhenSessionIdMissing(String sessionId) {
			// given
			String token = jwtProvider.createRefreshToken(MEMBER_ID, sessionId);

			// when & then
			assertThatThrownBy(() -> jwtProvider.parseRefreshToken(token))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
		}
	}
}
