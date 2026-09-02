package com.groove.global.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

	private static final String BEARER = "bearerAuth";

	@Bean
	public OpenAPI grooveOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("GROOVE API")
						.description("LP(바이닐) 전문 이커머스 REST API")
						.version("v1"))
				.components(new Components().addSecuritySchemes(BEARER,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

	@Bean
	public GroupedOpenApi publicApi() {
		return GroupedOpenApi.builder()
				.group("public")
				.pathsToMatch("/api/v1/**")
				.pathsToExclude("/api/v1/admin/**")
				.build();
	}

	@Bean
	public GroupedOpenApi adminApi() {
		return GroupedOpenApi.builder()
				.group("admin")
				.pathsToMatch("/api/v1/admin/**")
				.build();
	}
}
