package com.groove.limited.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** 한정반 선착순 구매의 재고/구매자 원자적 처리를 위한 Lua 스크립트 빈. */
@Configuration
public class LimitedDropRedisConfig {

	@Bean
	public RedisScript<Long> limitedReserveScript() {
		return RedisScript.of(new ClassPathResource("scripts/limited_reserve.lua"), Long.class);
	}

	@Bean
	public RedisScript<Long> limitedReleaseScript() {
		return RedisScript.of(new ClassPathResource("scripts/limited_release.lua"), Long.class);
	}
}
