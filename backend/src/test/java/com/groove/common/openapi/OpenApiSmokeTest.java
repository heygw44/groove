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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpringDoc(OpenAPI/Swagger UI) 도입 회귀 가드 (#156).
 *
 * <p>{@link com.groove.common.openapi.OpenApiConfig} 의 전역 OpenAPI 빈(제목·JWT Bearer 스킴)과
 * public/admin {@code GroupedOpenApi} 빈이 실제로 문서를 생성하는지, 그리고 {@code SecurityConfig} 가
 * swagger 경로를 인증 없이 여는지를 검증한다. 인증 헤더를 일절 보내지 않으므로 200 이면 permitAll 회귀도 함께 잡는다.
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
    @DisplayName("public 그룹 문서 → 200 + 제목·JWT Bearer 스킴, 공개 경로 포함·admin 경로 제외")
    void publicGroup_servesDocWithInfoAndSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
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
    @DisplayName("admin 그룹 문서 → 200 + admin 경로 포함·공개 전용 경로 제외")
    void adminGroup_servesOnlyAdminPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Groove API"))
                .andExpect(jsonPath("$.paths", aMapWithSize(greaterThan(0))))
                .andExpect(jsonPath("$.paths['/api/v1/coupons']").doesNotExist());
    }

    @Test
    @DisplayName("swagger-config → 200 + public/admin 그룹 노출")
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
