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
 * springdoc.api-docs.enabled / springdoc.swagger-ui.enabled 를 false 로 주입해 OpenAPI/Swagger 경로가 미등록(404)되는지 검증한다.
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
        // /swagger-ui.html 진입점도 404
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}
