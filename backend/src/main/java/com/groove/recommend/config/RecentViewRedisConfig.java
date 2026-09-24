package com.groove.recommend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/** 추천 도메인 Redis Lua 스크립트 빈. */
@Configuration
public class RecentViewRedisConfig {

	@Bean
	public RedisScript<Long> recentViewPushScript() {
		return RedisScript.of(new ClassPathResource("scripts/recent_view_push.lua"), Long.class);
	}

	@Bean
	public RedisScript<List> boughtTogetherScoresScript() {
		return RedisScript.of(new ClassPathResource("scripts/bought_together_scores.lua"), List.class);
	}
}
