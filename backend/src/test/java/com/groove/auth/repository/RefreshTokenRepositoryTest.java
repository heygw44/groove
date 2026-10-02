package com.groove.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

import com.groove.global.config.AuthSessionProperties;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.MemberRole;
import com.groove.support.IntegrationTestSupport;

class RefreshTokenRepositoryTest extends IntegrationTestSupport {

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Autowired
	JwtProperties jwtProperties;

	@Autowired
	AuthSessionProperties sessionProperties;

	private long memberId;
	private String sessionId;
	private String sessionKey;
	private String indexKey;
	private long now;
	private long farFutureAbsExp;

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
		now = System.currentTimeMillis();
		farFutureAbsExp = now + Duration.ofDays(30).toMillis();
	}

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("관리자 세션의 TTL 은 유휴 만료 시간 이하이고 일반 회원은 refresh 만료를 따른다")
		void appliesAdminIdleTimeoutAsTtl() {
			// given
			String userSessionId = UUID.randomUUID().toString();

			// when
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now, MemberRole.ADMIN);
			refreshTokenRepository.save(memberId, userSessionId, "token-b", farFutureAbsExp, now, MemberRole.USER);

			// then
			Long adminTtl = redisTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);
			Long userTtl = redisTemplate.getExpire("refresh:" + memberId + ":" + userSessionId, TimeUnit.MILLISECONDS);
			assertThat(adminTtl).isBetween(sessionProperties.adminIdleTimeout().minusSeconds(5).toMillis(),
					sessionProperties.adminIdleTimeout().toMillis());
			assertThat(userTtl).isBetween(jwtProperties.refreshTokenExpiry().minusSeconds(5).toMillis(),
					jwtProperties.refreshTokenExpiry().toMillis());
		}

		@Test
		@DisplayName("저장한 토큰은 findCurrent로 조회된다")
		void savedTokenIsFoundByFindCurrent() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

			// when
			Optional<String> found = refreshTokenRepository.findCurrent(memberId, sessionId);

			// then
			assertThat(found).contains("token-a");
		}

		@Test
		@DisplayName("이미 저장된 세션에 다시 저장하면 새 값으로 덮어쓴다")
		void overwritesExistingToken() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

			// when
			refreshTokenRepository.save(memberId, sessionId, "token-b", farFutureAbsExp, now);

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains("token-b");
		}

		@Test
		@DisplayName("세션 키와 인덱스 키에 만료 시간이 0보다 크고 설정된 TTL 이하로 설정된다")
		void setsExpireWithinConfiguredTtl() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

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
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

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
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

			// then
			assertThat(redisTemplate.opsForSet().members(indexKey)).containsExactly(sessionId);
		}

		@Test
		@DisplayName("abs_exp 를 기록하고 세션 키 TTL 은 절대 만료까지 남은 시간을 넘지 않는다")
		void recordsAbsExpAndCapsSessionTtl() {
			// given
			long nearAbsExp = now + 2000L;

			// when
			refreshTokenRepository.save(memberId, sessionId, "token-a", nearAbsExp, now);

			// then
			String absExp = (String) redisTemplate.opsForHash().get(sessionKey, "abs_exp");
			Long sessionTtlMillis = redisTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);
			assertThat(absExp).isEqualTo(String.valueOf(nearAbsExp));
			assertThat(sessionTtlMillis).isPositive();
			assertThat(sessionTtlMillis).isLessThanOrEqualTo(2000L);
		}
	}

	@Nested
	@DisplayName("rotate()")
	class Rotate {

		@Test
		@DisplayName("presented 가 current 와 같으면 새 토큰으로 회전한다")
		void rotatesWhenPresentedMatchesCurrent() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					System.currentTimeMillis(), farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.ROTATED);
			assertThat(rotation.refreshToken()).isEqualTo("token-b");
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains("token-b");
		}

		@Test
		@DisplayName("presented 가 prev 이고 grace 안이면 현재 토큰과 절대 만료 시각을 그대로 돌려준다")
		void returnsCurrentWhenPresentedIsPrevWithinGrace() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", now, farFutureAbsExp);

			// when
			RefreshRotation rotation =
					refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-c", now + 1000,
							farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.GRACE);
			assertThat(rotation.refreshToken()).isEqualTo("token-b");
			assertThat(rotation.absoluteExpiresAt()).isEqualTo(farFutureAbsExp);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains("token-b");
		}

		@Test
		@DisplayName("presented 가 prev 이지만 grace 를 지났으면 세션을 폐기한다")
		void discardsSessionWhenPresentedIsPrevAfterGrace() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", now, farFutureAbsExp);
			redisTemplate.opsForHash().put(sessionKey, "prev_exp", "0");

			// when
			RefreshRotation rotation =
					refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-c", now + 1000,
							farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.REUSED);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
			assertThat(redisTemplate.opsForSet().members(indexKey)).doesNotContain(sessionId);
		}

		@Test
		@DisplayName("인덱스 키가 지워진 뒤 회전하면 인덱스에 sessionId 가 다시 들어간다")
		void reAddsSessionIdToIndexWhenIndexKeyWasLost() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);
			redisTemplate.delete(indexKey);

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					System.currentTimeMillis(), farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.ROTATED);
			assertThat(redisTemplate.opsForSet().members(indexKey)).contains(sessionId);
		}

		@Test
		@DisplayName("관리자 세션을 회전하면 TTL 이 유휴 만료 시간으로 다시 걸린다")
		void extendsAdminSessionTtlOnRotate() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now, MemberRole.ADMIN);
			redisTemplate.expire(sessionKey, Duration.ofSeconds(10));

			// when
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", System.currentTimeMillis(),
					farFutureAbsExp, MemberRole.ADMIN);

			// then
			Long ttlMillis = redisTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);
			assertThat(ttlMillis).isBetween(sessionProperties.adminIdleTimeout().minusSeconds(5).toMillis(),
					sessionProperties.adminIdleTimeout().toMillis());
		}

		@Test
		@DisplayName("관리자 세션에 클라이언트 무입력 시간을 넘기면 세션 TTL 에서 그만큼 줄이고 인덱스 TTL 은 줄이지 않는다")
		void shortensAdminSessionTtlByClientIdleButNotIndex() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now, MemberRole.ADMIN);

			// when
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", System.currentTimeMillis(),
					farFutureAbsExp, MemberRole.ADMIN, Duration.ofMinutes(1));

			// then
			Duration expectedSessionTtl = sessionProperties.adminIdleTimeout().minusMinutes(1);
			Long sessionTtl = redisTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);
			Long indexTtl = redisTemplate.getExpire(indexKey, TimeUnit.MILLISECONDS);
			assertThat(sessionTtl).isBetween(expectedSessionTtl.minusSeconds(5).toMillis(),
					expectedSessionTtl.toMillis());
			assertThat(indexTtl).isBetween(sessionProperties.adminIdleTimeout().minusSeconds(5).toMillis(),
					sessionProperties.adminIdleTimeout().toMillis());
		}

		@Test
		@DisplayName("일반 회원 세션은 클라이언트 무입력 시간을 무시하고 refresh 만료를 따른다")
		void ignoresClientIdleForUser() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now, MemberRole.USER);

			// when
			refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b", System.currentTimeMillis(),
					farFutureAbsExp, MemberRole.USER, Duration.ofMinutes(1));

			// then
			Long sessionTtl = redisTemplate.getExpire(sessionKey, TimeUnit.MILLISECONDS);
			assertThat(sessionTtl).isBetween(jwtProperties.refreshTokenExpiry().minusSeconds(5).toMillis(),
					jwtProperties.refreshTokenExpiry().toMillis());
		}

		@Test
		@DisplayName("알 수 없는 토큰이면 세션을 폐기한다")
		void discardsSessionWhenPresentedIsUnknown() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "stolen", "token-b",
					System.currentTimeMillis(), farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.REUSED);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
		}

		@Test
		@DisplayName("세션이 없으면 NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenSessionMissing() {
			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					System.currentTimeMillis(), farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.NOT_FOUND);
			assertThat(rotation.refreshToken()).isNull();
		}

		@Test
		@DisplayName("절대 만료 시각이 지났으면 EXPIRED 를 반환하고 세션 키와 인덱스 멤버를 지운다")
		void returnsExpiredAndDeletesSessionWhenAbsExpElapsed() {
			// given
			// 세션 키의 실제 Redis TTL(sessionTtl = absExp - nowMillis)이 rotate 호출 전에 지워지지 않도록
			// 여유를 두고, abs_exp 자체는 rotate 시점의 now 보다 과거로 잡는다.
			long pastAbsExp = now - 100L;
			refreshTokenRepository.save(memberId, sessionId, "token-a", pastAbsExp, now - 5100L);

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					now, farFutureAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.EXPIRED);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
			assertThat(redisTemplate.opsForSet().members(indexKey)).doesNotContain(sessionId);
		}

		@Test
		@DisplayName("abs_exp 필드가 없는 레거시 세션은 legacyAbsExpMillis 로 채워지고 ROTATED 를 반환한다")
		void fillsLegacyAbsExpAndRotates() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);
			redisTemplate.opsForHash().delete(sessionKey, "abs_exp");
			long legacyAbsExp = now + 12345L;

			// when
			RefreshRotation rotation = refreshTokenRepository.rotate(memberId, sessionId, "token-a", "token-b",
					now, legacyAbsExp);

			// then
			assertThat(rotation.result()).isEqualTo(RotationResult.ROTATED);
			assertThat(rotation.absoluteExpiresAt()).isEqualTo(legacyAbsExp);
			String absExp = (String) redisTemplate.opsForHash().get(sessionKey, "abs_exp");
			assertThat(absExp).isEqualTo(String.valueOf(legacyAbsExp));
		}
	}

	@Nested
	@DisplayName("delete()")
	class Delete {

		@Test
		@DisplayName("삭제하면 findCurrent 결과가 비어있고 인덱스에서도 빠진다")
		void findCurrentReturnsEmptyAfterDelete() {
			// given
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);

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
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);
			refreshTokenRepository.save(memberId, otherSessionId, "token-b", farFutureAbsExp, now);

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
			refreshTokenRepository.save(memberId, sessionId, "token-a", farFutureAbsExp, now);
			refreshTokenRepository.save(memberId, otherSessionId, "token-b", farFutureAbsExp, now);

			// when
			refreshTokenRepository.deleteAllByMemberId(memberId);

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).isEmpty();
			assertThat(refreshTokenRepository.findCurrent(memberId, otherSessionId)).isEmpty();
			assertThat(redisTemplate.opsForSet().members(indexKey)).isNullOrEmpty();
		}
	}
}
