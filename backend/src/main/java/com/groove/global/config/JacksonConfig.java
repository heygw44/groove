package com.groove.global.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PATCH 요청에서 labelId 처럼 "생략/null/값" 삼중 상태를 구분하기 위해 JsonNullable 모듈을 등록한다. */
@Configuration
public class JacksonConfig {

	@Bean
	public JsonNullableModule jsonNullableModule() {
		return new JsonNullableModule();
	}
}
