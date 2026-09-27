package com.groove.auth.jwt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.groove.auth.repository.AccessTokenRevocationRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.MemberRole;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationCheckerTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	AccessTokenRevocationRepository accessTokenRevocationRepository;

	AccessTokenRevocationChecker accessTokenRevocationChecker;

	@Nested
	@DisplayName("check()")
	class Check {

		@Test
		@DisplayName("발급 시각이 폐기 시각보다 이전이면 AUTH_TOKEN_REVOKED 예외를 던진다")
		void throwsWhenIssuedBeforeRevoked() {
			// given
			accessTokenRevocationChecker = new AccessTokenRevocationChecker(accessTokenRevocationRepository);
			given(accessTokenRevocationRepository.findRevokedAt(MEMBER_ID)).willReturn(OptionalLong.of(100L));
			TokenClaims claims = new TokenClaims(MEMBER_ID, MemberRole.USER, 99L);

			// when & then
			assertThatThrownBy(() -> accessTokenRevocationChecker.check(claims))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED);
		}

		@Test
		@DisplayName("발급 시각이 폐기 시각과 같은 초면 통과한다")
		void passesWhenIssuedAtSameSecondAsRevoked() {
			// given
			accessTokenRevocationChecker = new AccessTokenRevocationChecker(accessTokenRevocationRepository);
			given(accessTokenRevocationRepository.findRevokedAt(MEMBER_ID)).willReturn(OptionalLong.of(100L));
			TokenClaims claims = new TokenClaims(MEMBER_ID, MemberRole.USER, 100L);

			// when & then
			assertThatCode(() -> accessTokenRevocationChecker.check(claims)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("발급 시각이 폐기 시각보다 이후면 통과한다")
		void passesWhenIssuedAfterRevoked() {
			// given
			accessTokenRevocationChecker = new AccessTokenRevocationChecker(accessTokenRevocationRepository);
			given(accessTokenRevocationRepository.findRevokedAt(MEMBER_ID)).willReturn(OptionalLong.of(100L));
			TokenClaims claims = new TokenClaims(MEMBER_ID, MemberRole.USER, 101L);

			// when & then
			assertThatCode(() -> accessTokenRevocationChecker.check(claims)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("폐기 이력이 없으면 통과한다")
		void passesWhenNoRevocationRecorded() {
			// given
			accessTokenRevocationChecker = new AccessTokenRevocationChecker(accessTokenRevocationRepository);
			given(accessTokenRevocationRepository.findRevokedAt(MEMBER_ID)).willReturn(OptionalLong.empty());
			TokenClaims claims = new TokenClaims(MEMBER_ID, MemberRole.USER, 100L);

			// when & then
			assertThatCode(() -> accessTokenRevocationChecker.check(claims)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("Redis 장애가 나도 예외를 던지지 않는다")
		void passesWhenRedisFails() {
			// given
			accessTokenRevocationChecker = new AccessTokenRevocationChecker(accessTokenRevocationRepository);
			given(accessTokenRevocationRepository.findRevokedAt(MEMBER_ID))
					.willThrow(new RedisConnectionFailureException("connection failed"));
			TokenClaims claims = new TokenClaims(MEMBER_ID, MemberRole.USER, 100L);

			// when & then
			assertThatCode(() -> accessTokenRevocationChecker.check(claims)).doesNotThrowAnyException();
		}
	}
}
