package com.groove.common.openapi;

import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpringDoc(OpenAPI/Swagger UI) 도입 회귀 가드 (#156).
 *
 * <p>{@link com.groove.common.openapi.OpenApiConfig} 의 전역 OpenAPI 빈(제목·JWT Bearer 스킴),
 * storefront/admin {@code GroupedOpenApi} 빈, 그리고 ProblemDetail content·operationId 커스터마이저가
 * 실제로 문서를 생성하는지, {@code SecurityConfig} 가 swagger 경로를 인증 없이 여는지를 검증한다.
 * 인증 헤더를 일절 보내지 않으므로 200 이면 permitAll 회귀도 함께 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("OpenAPI 문서 생성 스모크")
class OpenApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("storefront 그룹 문서 → 200 + 제목·JWT Bearer 스킴, 공개 경로 포함·admin 경로 제외")
    void storefrontGroup_servesDocWithInfoAndSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Groove API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                // 컨트롤러가 실제로 스캔됐는지 — 공개 경로는 포함, admin 경로는 제외.
                .andExpect(jsonPath("$.paths['/api/v1/coupons']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders']").doesNotExist());
    }

    @Test
    @DisplayName("admin 그룹 문서 → 200 + 실제 admin 경로 포함·공개 전용 경로 제외")
    void adminGroup_servesOnlyAdminPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Groove API"))
                // 그룹 필터 회귀를 잡기 위해 실제 admin 경로 존재를 단언(단순 size>0 으로는 누락을 못 잡음).
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons']").doesNotExist());
    }

    @Test
    @DisplayName("에러 응답에 RFC7807 ProblemDetail content 가 전역 커스터마이저로 주입됨")
    void errorResponses_carryProblemDetailContent() throws Exception {
        mockMvc.perform(get("/v3/api-docs/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists())
                // 컨트롤러에서 content 블록을 제거했어도, 비-2xx 응답에 problem+json 이 주입돼야 한다.
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']"
                        + ".content['application/problem+json']").exists());
    }

    @Test
    @DisplayName("operationId 가 <컨트롤러>_<메서드> 로 결정적 부여됨")
    void operationId_isDeterministic() throws Exception {
        mockMvc.perform(get("/v3/api-docs/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.operationId").value("auth_login"));
    }

    @Test
    @DisplayName("swagger-config → 200 + 그룹 목록 노출")
    void swaggerConfig_listsGroups() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls").isArray());
    }

    @Test
    @DisplayName("Swagger UI 정적 리소스 → 인증 없이 200 (SecurityConfig permitAll 회귀 가드)")
    void swaggerUi_isPubliclyServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
