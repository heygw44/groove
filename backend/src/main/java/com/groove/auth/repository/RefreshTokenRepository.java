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

	@SuppressWarnings("unchecked")
	public RefreshRotation rotate(Long memberId, String sessionId, String presented, String newToken,
			long nowMillis, long legacyAbsExpMillis, MemberRole role) {
		String graceMillis = String.valueOf(jwtProperties.refreshTokenGrace().toMillis());
		String ttlMillis = String.valueOf(sessionTtl(role).toMillis());
		List<Object> result = redisTemplate.execute(refreshRotateScript,
				List.of(sessionKey(memberId, sessionId), indexKey(memberId)),
				presented, newToken, String.valueOf(nowMillis), graceMillis, ttlMillis, sessionId,
				String.valueOf(legacyAbsExpMillis));
		return toRotation(result);
	}

	/** 관리자는 유휴 만료로 짧게 건다. 저장·회전마다 PEXPIRE 가 다시 걸려 마지막 활동 기준으로 슬라이딩한다. */
	private Duration sessionTtl(MemberRole role) {
		return role == MemberRole.ADMIN ? sessionProperties.adminIdleTimeout() : jwtProperties.refreshTokenExpiry();
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
