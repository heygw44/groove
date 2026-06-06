package com.groove.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc(OpenAPI 3 / Swagger UI) 전역 설정 (#156).
 *
 * <p>API 문서를 코드(컨트롤러·record DTO·Jakarta 검증 애노테이션)에서 자동 생성해 수기 명세
 * {@code docs/API.md} 와의 drift 를 원천 차단한다. {@code docs/API.md} 는 설계 의도/배경을 담은
 * 설계 명세로 유지하고, 본 문서는 "실행 가능한 실시간 문서"로 상호 보완한다.
 *
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON: {@code /v3/api-docs} (그룹: {@code /v3/api-docs/public}, {@code /v3/api-docs/admin})</li>
 * </ul>
 *
 * <p>JWT Bearer 보안 스킴({@code bearerAuth})만 전역에 <b>정의</b>하고(= Swagger UI 의 Authorize 버튼 활성),
 * 전역 {@code SecurityRequirement} 는 걸지 않는다. 보호가 필요한 엔드포인트는 각 컨트롤러에서
 * {@code @SecurityRequirement("bearerAuth")} 로 개별 표기해 공개 엔드포인트와 잠금 표시를 정확히 구분한다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Authorize 버튼 및 엔드포인트별 @SecurityRequirement 가 참조하는 보안 스킴 이름. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI grooveOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Groove API")
                        .version("v1")
                        .description("""
                                LP 전문 이커머스 백엔드 Groove 의 REST API 문서.

                                - 인증: `Authorization: Bearer {accessToken}` (우측 상단 Authorize 에 토큰 입력 후 try-out)
                                - 에러 응답: RFC 7807 `application/problem+json`
                                - 설계 의도/배경 명세는 `docs/API.md` 참고 (본 문서는 코드에서 자동 생성된 실행 문서)
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인(`POST /api/v1/auth/login`) 으로 발급받은 accessToken")));
    }

    /** 공개/회원 API 그룹 — 관리자 경로 제외. */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/admin/**")
                .build();
    }

    /** 관리자 전용 API 그룹. */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }
}
