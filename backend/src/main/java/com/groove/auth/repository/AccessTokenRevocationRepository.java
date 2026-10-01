package com.groove.auth.repository;

import java.time.Duration;
import java.util.OptionalLong;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.groove.global.config.JwtProperties;

import lombok.RequiredArgsConstructor;

/**
 * 회원 단위 Access Token 폐기 시각을 Redis 에 보관한다.
 * 키 {@code auth-revoked:{memberId}} 의 값은 폐기 시각(epoch 초)이고, TTL 이 지나면 해당 시각 이전에 발급된
 * 토큰은 이미 자연 만료되었으므로 저절로 사라진다.
 */
@Repository
@RequiredArgsConstructor
public class AccessTokenRevocationRepository {

	private static final String KEY_PREFIX = "auth-revoked:";
	private static final Duration TTL_MARGIN = Duration.ofMinutes(1);

	private final StringRedisTemplate redisTemplate;
	private final JwtProperties jwtProperties;

	public void markRevoked(Long memberId, long revokedAtEpochSecond) {
		Duration ttl = jwtProperties.maxAccessTokenExpiry().plus(TTL_MARGIN);
		redisTemplate.opsForValue().set(key(memberId), String.valueOf(revokedAtEpochSecond), ttl);
	}

	public OptionalLong findRevokedAt(Long memberId) {
		String value = redisTemplate.opsForValue().get(key(memberId));
		if (value == null) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(Long.parseLong(value));
	}

	private static String key(Long memberId) {
		return KEY_PREFIX + memberId;
	}
}
