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
 * SpringDoc(OpenAPI 3 / Swagger UI) 전역 설정 (#156).
 *
 * <p>API 문서를 코드(컨트롤러·record DTO·Jakarta 검증 애노테이션)에서 자동 생성해 수기 명세
 * {@code docs/API.md} 와의 drift 를 원천 차단한다. {@code docs/API.md} 는 설계 의도/배경을 담은
 * 설계 명세로 유지하고, 본 문서는 "실행 가능한 실시간 문서"로 상호 보완한다.
 *
 * <ul>
 *   <li>Swagger UI: {@code /swagger-ui.html}</li>
 *   <li>OpenAPI JSON: {@code /v3/api-docs} (그룹: {@code /v3/api-docs/storefront}, {@code /v3/api-docs/admin})</li>
 * </ul>
 *
 * <p>JWT Bearer 보안 스킴({@code bearerAuth})만 전역에 <b>정의</b>하고(= Swagger UI 의 Authorize 버튼 활성),
 * 전역 {@code SecurityRequirement} 는 걸지 않는다. 보호가 필요한 엔드포인트는 각 컨트롤러에서
 * {@code @SecurityRequirement("bearerAuth")} 로 개별 표기해 공개 엔드포인트와 잠금 표시를 정확히 구분한다.
 *
 * <p>에러 응답 스키마(RFC 7807 {@link ProblemDetail})와 {@code operationId} 는 컨트롤러마다 반복하지 않고
 * {@link #problemDetailErrorResponses()} · {@link #stableOperationId()} 커스터마이저로 일괄 부여한다.
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
                                - 설계 의도/배경 명세는 `docs/API.md` 참고 (본 문서는 코드에서 자동 생성된 실행 문서)
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인(`POST /api/v1/auth/login`) 으로 발급받은 accessToken")));
    }

    /** 고객용 API 그룹 — 공개 카탈로그 + 회원 인증 엔드포인트(관리자 경로 제외). */
    @Bean
    public GroupedOpenApi storefrontApi() {
        return GroupedOpenApi.builder()
                .group("storefront")
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

    /**
     * 모든 비-2xx 응답에 RFC 7807 {@link ProblemDetail} 스키마({@code application/problem+json})를 일괄 주입한다 (#156 리뷰).
     *
     * <p>에러 본문은 {@code GlobalExceptionHandler} 가 전역적으로 동일 포맷으로 내려주므로, 컨트롤러마다
     * {@code content = @Content(...)} 를 반복하는 대신 여기서 한 번에 부여한다. 엔드포인트는 응답 코드/설명만
     * 선언하면 된다. 이미 content 가 지정된 응답은 건드리지 않는다.
     */
    @Bean
    public GlobalOpenApiCustomizer problemDetailErrorResponses() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            // 엔드포인트가 더는 ProblemDetail 을 직접 참조하지 않으므로 스키마를 컴포넌트에 직접 등록한다.
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
                            // 비-2xx 는 전부 ProblemDetail. springdoc 이 채워둔 기본 content(*/*)를
                            // problem+json 으로 덮어써 일관된 에러 스키마를 보장한다.
                            boolean error = code.length() == 3 && !code.startsWith("2");
                            if (error) {
                                response.setContent(problemContent);
                            }
                        });
                    }));
        };
    }

    /**
     * {@code operationId} 를 {@code <컨트롤러>_<메서드>} 로 결정적으로 부여한다 (#156 리뷰).
     *
     * <p>여러 컨트롤러에 같은 메서드명({@code list}, {@code get} 등)이 있어 springdoc 기본값이
     * {@code list_1} 같은 비결정적 접미사를 붙이는 문제를 막아, 클라이언트 코드젠에 안정적인 식별자를 제공한다.
     */
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
