package com.groove.auth.security;

import com.groove.common.exception.ErrorCode;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfigIntegrationTest.SecuredPingController.class, TestcontainersConfig.class})
@DisplayName("SecurityFilterChain 통합 동작")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("보호된 엔드포인트 → 401 + ProblemDetail JSON + X-Request-Id")
    void protectedEndpoint_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ping/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("/actuator/health 공개 → 보안 통과 (401/403 아님)")
    void actuatorHealth_isPublic() throws Exception {
        int status = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("GET /api/v1/albums/** 공개 → 컨트롤러 미존재이므로 404 (보안 통과 확인)")
    void albumsGet_isPublic_butControllerMissing() throws Exception {
        mockMvc.perform(get("/api/v1/albums/123"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CORS 프리플라이트 → 200 + Allow-Origin")
    void corsPreflight_isAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/albums/123")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    @DisplayName("CSRF 비활성화 + 미인증 POST → 401 (403 아님)")
    void protectedPost_csrfDisabled_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ping/secured"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 보호 엔드포인트 검증용 테스트 전용 컨트롤러. 메인 코드에는 포함되지 않는다.
     */
    @RestController
    static class SecuredPingController {
        @GetMapping("/api/v1/ping/secured")
        public Map<String, Boolean> get() {
            return Map.of("pong", true);
        }

        @PostMapping("/api/v1/ping/secured")
        public Map<String, Boolean> post() {
            return Map.of("pong", true);
        }
    }
}
