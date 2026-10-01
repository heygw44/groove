package com.groove.global.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** 실패 카운터 증가를 위한 Lua 스크립트 빈. */
@Configuration
public class AttemptCounterRedisConfig {

	@Bean
	public RedisScript<Long> attemptIncrScript() {
		return RedisScript.of(new ClassPathResource("scripts/attempt_incr.lua"), Long.class);
	}
}
