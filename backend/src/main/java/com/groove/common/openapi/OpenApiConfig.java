package com.groove.common.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.Map;

/**
 * SpringDoc(OpenAPI 3 / Swagger UI) 전역 설정. API 문서를 코드(컨트롤러·record DTO·Jakarta 검증 애노테이션)에서
 * 자동 생성한다. Swagger UI=/swagger-ui.html, OpenAPI JSON=/v3/api-docs(그룹: storefront·admin).
 *
 * JWT Bearer 보안 스킴(bearerAuth)만 전역 정의하고 전역 SecurityRequirement 는 걸지 않는다 — 보호 엔드포인트는
 * 컨트롤러에서 @SecurityRequirement("bearerAuth") 로 개별 표기한다. 에러 응답 스키마(RFC 7807 ProblemDetail)와
 * operationId 는 problemDetailErrorResponses·stableOperationId 커스터마이저로 일괄 부여한다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Authorize 버튼 및 엔드포인트별 @SecurityRequirement 가 참조하는 보안 스킴 이름. */
    public static final String BEARER_SCHEME = "bearerAuth";

    private static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

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
                                - 본 문서는 코드에서 자동 생성된 실행 문서
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인(`POST /api/v1/auth/login`) 으로 발급받은 accessToken")));
    }

    @Bean
    public GroupedOpenApi storefrontApi() {
        return GroupedOpenApi.builder()
                .group("storefront")
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

    /** 모든 비-2xx 응답에 RFC 7807 ProblemDetail 스키마(application/problem+json)를 일괄 주입한다. 이미 content 가 지정된 응답은 건드리지 않는다. */
    @Bean
    public GlobalOpenApiCustomizer problemDetailErrorResponses() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            // ProblemDetail 스키마를 컴포넌트에 등록한다.
            if (components.getSchemas() == null || !components.getSchemas().containsKey(PROBLEM_DETAIL_SCHEMA)) {
                Map<String, Schema> problemSchemas = ModelConverters.getInstance().read(ProblemDetail.class);
                problemSchemas.forEach(components::addSchemas);
            }
            Content problemContent = new Content().addMediaType(
                    "application/problem+json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA)));

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            return;
                        }
                        operation.getResponses().forEach((code, response) -> {
                            boolean error = code.length() == 3 && !code.startsWith("2");
                            if (!error) {
                                return;
                            }
                            // 기본 placeholder(*/*) 또는 미지정일 때만 problem+json 으로 채운다.
                            Content existing = response.getContent();
                            boolean replaceableDefault = existing != null
                                    && existing.size() == 1
                                    && existing.get("*/*") != null;
                            if (existing == null || replaceableDefault) {
                                response.setContent(problemContent);
                            }
                        });
                    }));
        };
    }

    /** operationId 를 <컨트롤러>_<메서드> 로 부여한다. */
    @Bean
    public GlobalOperationCustomizer stableOperationId() {
        return (operation, handlerMethod) -> {
            String type = handlerMethod.getBeanType().getSimpleName();
            if (type.endsWith("Controller")) {
                type = type.substring(0, type.length() - "Controller".length());
            }
            String prefix = type.isEmpty()
                    ? "op"
                    : Character.toLowerCase(type.charAt(0)) + type.substring(1);
            operation.setOperationId(prefix + "_" + handlerMethod.getMethod().getName());
            return operation;
        };
    }
}
