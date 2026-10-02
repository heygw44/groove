package com.groove.global.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 키 단위 실패 횟수를 Redis 에 보관한다. 키 값은 실패 횟수이고, 실패마다 TTL 을 다시 걸어 마지막 실패로부터
 * window 가 지나면 자연히 풀린다. 접두사는 호출하는 가드가 정한다(예: {@code login-fail:m:{id}}).
 */
@Repository
@RequiredArgsConstructor
public class AttemptCounterRepository {

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> attemptIncrScript;

	public int count(String key) {
		String value = redisTemplate.opsForValue().get(key);
		return value == null ? 0 : Integer.parseInt(value);
	}

	public long increment(String key, Duration window) {
		return redisTemplate.execute(attemptIncrScript, List.of(key), String.valueOf(window.toMillis()));
	}

	public void delete(String key) {
		redisTemplate.delete(key);
	}
}
