package com.groove.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.global.config.JwtProperties;
import com.groove.support.IntegrationTestSupport;

class AccessTokenRevocationRepositoryTest extends IntegrationTestSupport {

	@Autowired
	AccessTokenRevocationRepository accessTokenRevocationRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Autowired
	JwtProperties jwtProperties;

	private long memberId;
	private String key;

	/** Redis 는 싱글턴 Testcontainers 를 테스트 전체가 공유하므로 memberId 를 매번 새로 발급한다. */
	@BeforeEach
	void setUp() {
		memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		key = "auth-revoked:" + memberId;
	}

	@Nested
	@DisplayName("markRevoked()")
	class MarkRevoked {

		@Test
		@DisplayName("기록한 폐기 시각은 findRevokedAt으로 조회된다")
		void recordedValueIsFoundByFindRevokedAt() {
			// given
			accessTokenRevocationRepository.markRevoked(memberId, 1_700_000_000L);

			// when
			OptionalLong found = accessTokenRevocationRepository.findRevokedAt(memberId);

			// then
			assertThat(found).hasValue(1_700_000_000L);
		}

		@Test
		@DisplayName("다시 기록하면 새 값으로 덮어쓴다")
		void overwritesExistingValue() {
			// given
			accessTokenRevocationRepository.markRevoked(memberId, 1_700_000_000L);

			// when
			accessTokenRevocationRepository.markRevoked(memberId, 1_700_000_100L);

			// then
			assertThat(accessTokenRevocationRepository.findRevokedAt(memberId)).hasValue(1_700_000_100L);
		}

		@Test
		@DisplayName("TTL 은 accessTokenExpiry 에 1분을 더한 값 이하로 설정된다")
		void setsExpireWithinAccessTokenExpiryPlusMargin() {
			// given
			accessTokenRevocationRepository.markRevoked(memberId, 1_700_000_000L);

			// when
			Long expireSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

			// then
			long expectedMax = jwtProperties.accessTokenExpiry().toSeconds() + 60;
			assertThat(expireSeconds).isPositive();
			assertThat(expireSeconds).isLessThanOrEqualTo(expectedMax);
		}
	}

	@Nested
	@DisplayName("findRevokedAt()")
	class FindRevokedAt {

		@Test
		@DisplayName("기록이 없으면 빈 값을 반환한다")
		void returnsEmptyWhenNoRecord() {
			// when & then
			assertThat(accessTokenRevocationRepository.findRevokedAt(memberId)).isEmpty();
		}
	}
}
