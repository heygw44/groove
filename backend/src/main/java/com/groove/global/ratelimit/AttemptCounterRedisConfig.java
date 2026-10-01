package com.groove.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** 로그인 실패 카운터 증가를 위한 Lua 스크립트 빈. */
@Configuration
public class LoginAttemptRedisConfig {

	@Bean
	public RedisScript<Long> loginFailIncrScript() {
		return RedisScript.of(new ClassPathResource("scripts/login_fail_incr.lua"), Long.class);
	}
}
