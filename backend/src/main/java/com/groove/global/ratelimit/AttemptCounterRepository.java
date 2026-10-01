package com.groove.auth.repository;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 이메일 단위 로그인 실패 횟수를 Redis 에 보관한다.
 * 키 {@code login-fail:{email}} 의 값은 실패 횟수이고, 실패마다 TTL 을 다시 걸어 마지막 실패로부터
 * window 가 지나면 자연히 풀린다.
 */
@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

	private static final String KEY_PREFIX = "login-fail:";

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> loginFailIncrScript;

	public int count(String emailKey) {
		String value = redisTemplate.opsForValue().get(key(emailKey));
		return value == null ? 0 : Integer.parseInt(value);
	}

	public long increment(String emailKey, Duration window) {
		return redisTemplate.execute(loginFailIncrScript, List.of(key(emailKey)),
				String.valueOf(window.toMillis()));
	}

	public void delete(String emailKey) {
		redisTemplate.delete(key(emailKey));
	}

	private static String key(String emailKey) {
		return KEY_PREFIX + emailKey;
	}
}
