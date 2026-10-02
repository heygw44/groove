package com.groove.auth.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import com.groove.global.config.AuthSessionProperties;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.MemberRole;

import lombok.RequiredArgsConstructor;

/**
 * Redis 에 회원의 로그인 세션별 Refresh Token 을 보관한다.
 * 세션 키(Hash) {@code refresh:{memberId}:{sessionId}} 는 current/prev/prev_exp/abs_exp 필드를 갖고,
 * 인덱스 키(Set) {@code refresh-sessions:{memberId}} 는 회원의 살아있는 sessionId 목록을 갖는다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

	private static final String SESSION_KEY_PREFIX = "refresh:";
	private static final String INDEX_KEY_PREFIX = "refresh-sessions:";
	private static final String FIELD_CURRENT = "current";
	private static final Duration MIN_SESSION_TTL = Duration.ofSeconds(1);

	private final StringRedisTemplate redisTemplate;
	private final JwtProperties jwtProperties;
	private final AuthSessionProperties sessionProperties;
	private final RedisScript<Long> refreshSaveScript;
	private final RedisScript<List> refreshRotateScript;

	public void save(Long memberId, String sessionId, String token, long absExpMillis, long nowMillis) {
		save(memberId, sessionId, token, absExpMillis, nowMillis, MemberRole.USER);
	}

	public void save(Long memberId, String sessionId, String token, long absExpMillis, long nowMillis,
			MemberRole role) {
		String ttlMillis = String.valueOf(sessionTtl(role).toMillis());
		redisTemplate.execute(refreshSaveScript,
				List.of(sessionKey(memberId, sessionId), indexKey(memberId)),
				token, sessionId, ttlMillis, sessionKeyPrefix(memberId),
				String.valueOf(absExpMillis), String.valueOf(nowMillis));
	}

	public RefreshRotation rotate(Long memberId, String sessionId, String presented, String newToken,
			long nowMillis, long legacyAbsExpMillis) {
		return rotate(memberId, sessionId, presented, newToken, nowMillis, legacyAbsExpMillis, MemberRole.USER);
	}

	public RefreshRotation rotate(Long memberId, String sessionId, String presented, String newToken,
			long nowMillis, long legacyAbsExpMillis, MemberRole role) {
		return rotate(memberId, sessionId, presented, newToken, nowMillis, legacyAbsExpMillis, role, Duration.ZERO);
	}

	@SuppressWarnings("unchecked")
	public RefreshRotation rotate(Long memberId, String sessionId, String presented, String newToken,
			long nowMillis, long legacyAbsExpMillis, MemberRole role, Duration clientIdle) {
		String graceMillis = String.valueOf(jwtProperties.refreshTokenGrace().toMillis());
		String sessionTtlMillis = String.valueOf(sessionTtl(role, clientIdle).toMillis());
		// 인덱스는 회원의 모든 세션이 공유하므로 한 세션의 유휴 시간으로 줄이지 않고 역할별 전체 TTL 을 유지한다.
		String indexTtlMillis = String.valueOf(sessionTtl(role).toMillis());
		List<Object> result = redisTemplate.execute(refreshRotateScript,
				List.of(sessionKey(memberId, sessionId), indexKey(memberId)),
				presented, newToken, String.valueOf(nowMillis), graceMillis, sessionTtlMillis, sessionId,
				String.valueOf(legacyAbsExpMillis), indexTtlMillis);
		return toRotation(result);
	}

	/** 관리자는 유휴 만료로 짧게 건다. 저장·회전마다 PEXPIRE 가 다시 걸려 마지막 활동 기준으로 슬라이딩한다. */
	private Duration sessionTtl(MemberRole role) {
		return role == MemberRole.ADMIN ? sessionProperties.adminIdleTimeout() : jwtProperties.refreshTokenExpiry();
	}

	/** 관리자 세션은 클라이언트가 보고한 무입력 시간만큼 남은 유휴 허용치를 줄인다. 일반 회원은 무시한다. */
	private Duration sessionTtl(MemberRole role, Duration clientIdle) {
		if (role != MemberRole.ADMIN) {
			return sessionTtl(role);
		}
		Duration remaining = sessionProperties.adminIdleTimeout().minus(clientIdle);
		return remaining.compareTo(MIN_SESSION_TTL) < 0 ? MIN_SESSION_TTL : remaining;
	}

	public void delete(Long memberId, String sessionId) {
		redisTemplate.delete(sessionKey(memberId, sessionId));
		redisTemplate.opsForSet().remove(indexKey(memberId), sessionId);
	}

	public void deleteAllByMemberId(Long memberId) {
		Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey(memberId));
		List<String> keysToDelete = new ArrayList<>();
		if (sessionIds != null) {
			sessionIds.forEach(sessionId -> keysToDelete.add(sessionKey(memberId, sessionId)));
		}
		keysToDelete.add(indexKey(memberId));
		redisTemplate.delete(keysToDelete);
	}

	public Optional<String> findCurrent(Long memberId, String sessionId) {
		HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
		return Optional.ofNullable(hashOps.get(sessionKey(memberId, sessionId), FIELD_CURRENT));
	}

	private static RefreshRotation toRotation(List<Object> result) {
		if (result == null || result.isEmpty()) {
			throw new IllegalStateException("refresh_rotate 스크립트가 알 수 없는 값을 반환했습니다: " + result);
		}
		int code = ((Long) result.get(0)).intValue();
		return switch (code) {
			case 0 -> new RefreshRotation(RotationResult.NOT_FOUND, null, 0L);
			case 1 -> new RefreshRotation(RotationResult.ROTATED, (String) result.get(1), (Long) result.get(2));
			case 2 -> new RefreshRotation(RotationResult.GRACE, (String) result.get(1), (Long) result.get(2));
			case 3 -> new RefreshRotation(RotationResult.REUSED, null, 0L);
			case 4 -> new RefreshRotation(RotationResult.EXPIRED, null, 0L);
			default -> throw new IllegalStateException("알 수 없는 refresh_rotate 결과 코드: " + code);
		};
	}

	private static String sessionKey(Long memberId, String sessionId) {
		return sessionKeyPrefix(memberId) + sessionId;
	}

	private static String sessionKeyPrefix(Long memberId) {
		return SESSION_KEY_PREFIX + memberId + ":";
	}

	private static String indexKey(Long memberId) {
		return INDEX_KEY_PREFIX + memberId;
	}
}
