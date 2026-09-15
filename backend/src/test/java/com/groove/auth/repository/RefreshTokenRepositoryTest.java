package com.groove.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
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

class RefreshTokenRepositoryTest extends IntegrationTestSupport {

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Autowired
	JwtProperties jwtProperties;

	private long memberId;
	private String sessionId;
	private String sessionKey;
	private String indexKey;

	/**
	 * Redis 는 싱글턴 Testcontainers 를 테스트 전체가 공유한다. memberId 를 고정하면 앞선 테스트가 심어둔
	 * (아직 살아있는) 다른 세션이 인덱스에 남아있어 뒤에 도는 테스트를 오염시키므로 매번 새로 발급한다.
	 */
	@BeforeEach
	void setUp() {
		memberId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		sessionId = UUID.randomUUID().toString();
		sessionKey = "refresh:" + memberId + ":" + sessionId;
		indexKey = "refresh-sessions:" + memberId;
	}

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("저장한 토큰은 findCurrent로 조회된다")
		void savedTokenIsFoundByFindCurrent() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// when
			Optional<String> found = refreshTokenRepository.findCurrent(memberId, sessionId);

			// then
			assertThat(found).contains("token-a");
		}

		@Test
		@DisplayName("이미 저장된 세션에 다시 저장하면 새 값으로 덮어쓴다")
		void overwritesExistingToken() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// when
			refreshTokenRepository.save(memberId, sessionId, "token-b");

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains("token-b");
		}

		@Test
		@DisplayName("세션 키와 인덱스 키에 만료 시간이 0보다 크고 설정된 TTL 이하로 설정된다")
		void setsExpireWithinConfiguredTtl() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// when
			Long sessionExpireSeconds = redisTemplate.getExpire(sessionKey, TimeUnit.SECONDS);
			Long indexExpireSeconds = redisTemplate.getExpire(indexKey, TimeUnit.SECONDS);

			// then
			assertThat(sessionExpireSeconds).isPositive();
			assertThat(sessionExpireSeconds).isLessThanOrEqualTo(jwtProperties.refreshTokenExpiry().toSeconds());
			assertThat(indexExpireSeconds).isPositive();
			assertThat(indexExpireSeconds).isLessThanOrEqualTo(jwtProperties.refreshTokenExpiry().toSeconds());
		}

		@Test
		@DisplayName("인덱스에는 sessionId 가 등록된다")
		void registersSessionIdInIndex() {
			// when
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// then
			assertThat(redisTemplate.opsForSet().members(indexKey)).contains(sessionId);
		}

		@Test
		@DisplayName("인덱스에 남은 죽은 sessionId 는 정리된다")
		void cleansUpDeadSessionIdsInIndex() {
			// given
			String deadSessionId = UUID.randomUUID().toString();
			redisTemplate.opsForSet().add(indexKey, deadSessionId);

			// when
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// then
			assertThat(redisTemplate.opsForSet().members(indexKey)).containsExactly(sessionId);
		}
	}

	@Nested
	@DisplayName("rotate()")
	class Rotate {

		@Test
		@DisplayName("presented 가 current 와 같으면 새 토큰으로 회전한다")
		void rotatesWhenPresentedMatchesCurrent() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					System.currentTimeMillis());

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.ROTATED);
			assertThat(rotation.refreshToken()).isEqualTo("token-b");
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains("token-b");
		}

		@Test
		@DisplayName("presented 가 prev 이고 grace 안이면 현재 토큰을 그대로 돌려준다")
		void returnsCurrentWhenPresentedIsPrevWithinGrace() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");
			long now = System.currentTimeMillis();
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", now);

			// when
			RefreshRotation rotation =
					refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-c", now + 1000);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.GRACE);
			assertThat(rotation.refreshToken()).isEqualTo("token-b");
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains("token-b");
		}

		@Test
		@DisplayName("presented 가 prev 이지만 grace 를 지났으면 세션을 폐기한다")
		void discardsSessionWhenPresentedIsPrevAfterGrace() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");
			long now = System.currentTimeMillis();
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", now);
			redisTemplate.opsForHash().put(sessionKey, "prev_exp", "0");

			// when
			RefreshRotation rotation =
					refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-c", now + 1000);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.REUSED);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
			assertThat(redisTemplate.opsForSet().members(indexKey)).doesNotContain(sessionId);
		}

		@Test
		@DisplayName("인덱스 키가 지워진 뒤 회전하면 인덱스에 sessionId 가 다시 들어간다")
		void reAddsSessionIdToIndexWhenIndexKeyWasLost() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");
			redisTemplate.delete(indexKey);

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					System.currentTimeMillis());

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.ROTATED);
			assertThat(redisTemplate.opsForSet().members(indexKey)).contains(sessionId);
		}

		@Test
		@DisplayName("알 수 없는 토큰이면 세션을 폐기한다")
		void discardsSessionWhenPresentedIsUnknown() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "stolen", "token-b",
					System.currentTimeMillis());

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.REUSED);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
		}

		@Test
		@DisplayName("세션이 없으면 NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenSessionMissing() {
			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					System.currentTimeMillis());

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.NOT_FOUND);
			assertThat(rotation.refreshToken()).isNull();
		}
	}

	@Nested
	@DisplayName("delete()")
	class Delete {

		@Test
		@DisplayName("삭제하면 findCurrent 결과가 비어있고 인덱스에서도 빠진다")
		void findCurrentReturnsEmptyAfterDelete() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a");

			// when
			refreshTokenRepository.delete(memberId, sessionId);

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
			assertThat(redisTemplate.opsForSet().members(indexKey)).doesNotContain(sessionId);
		}

		@Test
		@DisplayName("다른 세션에는 영향을 주지 않는다")
		void doesNotAffectOtherSessions() {
			// given
			String otherSessionId = UUID.randomUUID().toString();
			refreshTokenRepository.save(memberId, sessionId, "token-a");
			refreshTokenRepository.save(memberId, otherSessionId, "token-b");

			// when
			refreshTokenRepository.delete(memberId, sessionId);

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, otherSessionId)).contains("token-b");
		}
	}

	@Nested
	@DisplayName("deleteAllByMemberId()")
	class DeleteAllByMemberId {

		@Test
		@DisplayName("회원의 모든 세션과 인덱스를 지운다")
		void deletesAllSessionsAndIndex() {
			// given
			String otherSessionId = UUID.randomUUID().toString();
			refreshTokenRepository.save(memberId, sessionId, "token-a");
			refreshTokenRepository.save(memberId, otherSessionId, "token-b");

			// when
			refreshTokenRepository.deleteAllByMemberId(memberId);

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
			assertThat(refreshTokenRepository.findCurrent(memberId, otherSessionId)).isEmpty();
			assertThat(redisTemplate.opsForSet().members(indexKey)).isNullOrEmpty();
		}
	}
}
