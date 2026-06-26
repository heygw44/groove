package com.groove.auth.security;

import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.MemberRole;
import com.groove.support.TestcontainersConfig;
import com.groove.web.SpaRoutes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.http.server.PathContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("유효한 Access Token 으로 보호 엔드포인트 → 200")
    void validAccessToken_grantsAccess() throws Exception {
        String token = jwtProvider.issueAccessToken(1L, MemberRole.USER);

        mockMvc.perform(get("/api/v1/ping/secured")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pong").value(true));
    }

    @Test
    @DisplayName("위조된 토큰으로 보호 엔드포인트 → 401")
    void invalidToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ping/secured")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("Refresh Token 으로 보호 엔드포인트 접근 → 401 (typ mismatch)")
    void refreshToken_onProtectedEndpoint_returnsUnauthorized() throws Exception {
        String refresh = jwtProvider.issueRefreshToken(1L);

        mockMvc.perform(get("/api/v1/ping/secured")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

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
    @DisplayName("정적 SPA 셸(GET /, /index.html) 공개 → 200 서빙 (#113 Vite 빌드 산출물)")
    void staticSpaShell_isPublic() throws Exception {
        // "/" 는 welcome-page 로 index.html 이 서빙되어 200.
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("정적 permitAll 은 GET 한정 → 정적 경로 POST 는 그대로 401 (#102)")
    void staticSpaPaths_postIsStillProtected() throws Exception {
        mockMvc.perform(post("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SPA clean-route(GET /cart) → index.html 로 forward (#113 history fallback)")
    void spaCleanRoute_forwardsToIndexHtml() throws Exception {
        // /cart 진입 시 SpaForwardConfig 가 index.html 로 forward 하고 GET permitAll 로 SPA 셸이 로드된다.
        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("SPA admin clean-route(GET /admin) HTML 셸 공개 → forward (실데이터는 API 가 보호)")
    void spaAdminRoute_isPublicShell() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("SPA /admin 셸이 공개여도 /api/v1/admin/** 는 비인증 차단 유지 (API 정책 불변)")
    void apiAdminPath_stillProtected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 /api/v1/** 는 SPA forward 대상 아님 → HTML 셸이 아닌 401 (API/SPA 격리)")
    void unknownApiPath_isNotForwardedToSpa() throws Exception {
        // /api 는 SpaRoutes.PATTERNS 에 없어 forward 되지 않고 인증 정책(401)을 탄다.
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(forwardedUrl(null));
    }

    @Test
    @DisplayName("SpaRoutes.PATTERNS 와 충돌하는 GET 컨트롤러 매핑이 없어야 한다 (SPA permitAll 회귀 가드)")
    void noGetControllerCollidesWithSpaRoutes(
            @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        // SpaRoutes.PATTERNS 와 충돌하는 bare GET 컨트롤러 매핑이 생기면 빌드를 깨뜨린다.
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            var methods = info.getMethodsCondition().getMethods();
            boolean handlesGet = methods.isEmpty() || methods.contains(RequestMethod.GET);
            if (!handlesGet) {
                continue;
            }
            var patternsCondition = info.getPathPatternsCondition();
            if (patternsCondition == null) {
                continue;
            }
            for (PathPattern controllerPattern : patternsCondition.getPatterns()) {
                for (String spaRoute : SpaRoutes.PATTERNS) {
                    // base 경로와 하위 경로 둘 다로 컨트롤러 매칭을 시도한다.
                    for (String probe : List.of(
                            spaRoute.replace("/**", "").replace("/*", ""),
                            spaRoute.replace("/**", "/probe").replace("/*", "/probe"))) {
                        if (probe.isEmpty()) {
                            continue;
                        }
                        if (controllerPattern.matches(PathContainer.parsePath(probe))) {
                            fail("GET 컨트롤러 매핑 '" + controllerPattern.getPatternString()
                                    + "' 가 SPA permitAll 경로 '" + spaRoute
                                    + "' 와 충돌합니다. SpaRoutes.PATTERNS 가 실데이터 GET 을 덮지 않도록 분리하세요.");
                        }
                    }
                }
            }
        }
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

    @Test
    @DisplayName("CSP 기본 Report-Only 헤더 발급 + Toss 도메인 허용 (#322), enforce 헤더는 미발급")
    void csp_reportOnlyHeaderIsIssued() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy-Report-Only"))
                .andExpect(header().string("Content-Security-Policy-Report-Only",
                        org.hamcrest.Matchers.containsString("https://*.tosspayments.com")))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
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
