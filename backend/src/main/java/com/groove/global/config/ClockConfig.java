package com.groove.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 서버 시각 소스. 한정반 카운트다운 등 클라이언트가 아닌 서버 기준 시각이 필요한 곳에서 주입받는다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
