package com.groove.auth.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** Refresh Token 세션 저장/회전을 위한 Lua 스크립트 빈. */
@Configuration
public class RefreshTokenRedisConfig {

	@Bean
	public RedisScript<Long> refreshSaveScript() {
		return RedisScript.of(new ClassPathResource("scripts/refresh_save.lua"), Long.class);
	}

	@Bean
	public RedisScript<List> refreshRotateScript() {
		return RedisScript.of(new ClassPathResource("scripts/refresh_rotate.lua"), List.class);
	}
}
