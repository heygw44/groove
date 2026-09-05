package com.groove.recommend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** 최근 조회 상품 리스트 갱신을 위한 Lua 스크립트 빈. */
@Configuration
public class RecentViewRedisConfig {

	@Bean
	public RedisScript<Long> recentViewPushScript() {
		return RedisScript.of(new ClassPathResource("scripts/recent_view_push.lua"), Long.class);
	}
}
