package com.groove.common.openapi;

import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpringDoc 비활성화 회귀 가드 (#162).
 *
 * <p>배포(docker) 프로파일은 {@code springdoc.api-docs.enabled} / {@code springdoc.swagger-ui.enabled}
 * 를 {@code SPRINGDOC_ENABLED}(기본 false)로 끈다 — 전 API 표면(경로·파라미터·DTO·admin 그룹)의
 * 익명 노출 차단. 여기서는 그 두 프로퍼티를 직접 false 로 주입해 OpenAPI/Swagger 경로가 미등록(404)되는지
 * 검증한다. {@link OpenApiSmokeTest}(노출 시 200)와 짝을 이뤄 양방향 회귀를 잡는다.
 *
 * <p>docker 프로파일 자체는 환경 변수(JWT_SECRET 등) 의존이 커서 테스트가 부적합하므로, test 프로파일에
 * 동일 프로퍼티만 override 해 동작을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@DisplayName("SpringDoc 비활성화 시 문서 경로 차단")
class OpenApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("storefront/admin OpenAPI JSON → 비활성 시 404")
    void apiDocsGroups_areNotServedWhenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs/storefront"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Swagger UI 정적 리소스·진입점 → 비활성 시 404")
    void swaggerUi_isNotServedWhenDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
        // README 가 안내하는 진입점도 함께 가드한다 (springdoc 의 /swagger-ui.html → index.html 리다이렉트).
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}
