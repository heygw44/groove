package com.groove.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.global.config.JwtProperties;
import com.groove.support.IntegrationTestSupport;

class RefreshTokenRepositoryTest extends IntegrationTestSupport {

	private static final long MEMBER_ID = 1L;
	private static final String KEY = "refresh:" + MEMBER_ID;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Autowired
	JwtProperties jwtProperties;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("저장한 토큰은 findByMemberId로 조회된다")
		void savedTokenIsFoundByMemberId() {
			// given
			refreshTokenRepository.save(MEMBER_ID, "token-a");

			// when
			Optional<String> found = refreshTokenRepository.findByMemberId(MEMBER_ID);

			// then
			assertThat(found).contains("token-a");
		}

		@Test
		@DisplayName("이미 저장된 회원에 다시 저장하면 새 값으로 덮어쓴다")
		void overwritesExistingToken() {
			// given
			refreshTokenRepository.save(MEMBER_ID, "token-a");

			// when
			refreshTokenRepository.save(MEMBER_ID, "token-b");

			// then
			assertThat(refreshTokenRepository.findByMemberId(MEMBER_ID)).contains("token-b");
		}

		@Test
		@DisplayName("만료 시간이 0보다 크고 설정된 TTL 이하로 설정된다")
		void setsExpireWithinConfiguredTtl() {
			// given
			refreshTokenRepository.save(MEMBER_ID, "token-a");

			// when
			Long expireSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);

			// then
			assertThat(expireSeconds).isPositive();
			assertThat(expireSeconds).isLessThanOrEqualTo(jwtProperties.refreshTokenExpiry().toSeconds());
		}
	}

	@Nested
	@DisplayName("deleteByMemberId()")
	class DeleteByMemberId {

		@Test
		@DisplayName("삭제하면 findByMemberId 결과가 비어있다")
		void findByMemberIdReturnsEmptyAfterDelete() {
			// given
			refreshTokenRepository.save(MEMBER_ID, "token-a");

			// when
			refreshTokenRepository.deleteByMemberId(MEMBER_ID);

			// then
			assertThat(refreshTokenRepository.findByMemberId(MEMBER_ID)).isEmpty();
		}
	}
}
