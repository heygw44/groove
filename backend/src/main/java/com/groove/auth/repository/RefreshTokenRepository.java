package com.groove.auth.repository;

import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.groove.global.config.JwtProperties;

import lombok.RequiredArgsConstructor;

/** Redis 에 회원별 Refresh Token 을 보관한다. 재저장(Rotation)은 기존 값을 덮어쓴다. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

	private static final String KEY_PREFIX = "refresh:";

	private final StringRedisTemplate redisTemplate;
	private final JwtProperties jwtProperties;

	public void save(Long memberId, String refreshToken) {
		redisTemplate.opsForValue().set(key(memberId), refreshToken, jwtProperties.refreshTokenExpiry());
	}

	public Optional<String> findByMemberId(Long memberId) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(key(memberId)));
	}

	public void deleteByMemberId(Long memberId) {
		redisTemplate.delete(key(memberId));
	}

	private String key(Long memberId) {
		return KEY_PREFIX + memberId;
	}
}
