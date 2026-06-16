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

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 문서 생성(제목·JWT Bearer 스킴·그룹·ProblemDetail content·operationId)과 swagger 경로 인증 없는 접근을 검증한다.
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
                // 공개 경로는 포함, admin 경로는 제외
                .andExpect(jsonPath("$.paths['/api/v1/coupons']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders']").doesNotExist());
    }

    @Test
    @DisplayName("admin 그룹 문서 → 200 + 실제 admin 경로 포함·공개 전용 경로 제외")
    void adminGroup_servesOnlyAdminPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Groove API"))
                // admin 경로는 포함, 공개 전용 경로는 제외
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons']").doesNotExist());
    }

    @Test
    @DisplayName("에러 응답에 RFC7807 ProblemDetail content 가 전역 커스터마이저로 주입됨")
    void errorResponses_carryProblemDetailContent() throws Exception {
        mockMvc.perform(get("/v3/api-docs/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail").exists())
                // 비-2xx 응답에 problem+json content 가 주입됨
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
                .andExpect(jsonPath("$.urls").isArray())
                // 그룹명 노출 확인
                .andExpect(jsonPath("$.urls[*].name", hasItems("storefront", "admin")));
    }

    @Test
    @DisplayName("Swagger UI 정적 리소스 → 인증 없이 200 (SecurityConfig permitAll 회귀 가드)")
    void swaggerUi_isPubliclyServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
