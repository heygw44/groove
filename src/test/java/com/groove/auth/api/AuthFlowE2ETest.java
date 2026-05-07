package com.groove.auth.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestSecuredController;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 도메인 E2E 시나리오 (#24).
 *
 * <p>회원가입 → 로그인 → 보호 API → 토큰 회전 → 로그아웃 까지 실 필터·서비스·DB 를 모두 거쳐 검증한다.
 * Testcontainers 위에서 동작하며 보호 엔드포인트는 {@link TestSecuredController}(test 프로파일 한정).
 *
 * <p>토큰 회전 후 구 refresh 재사용은 {@code RefreshTokenServiceTest} 에서 단위 레벨로 다루므로
 * 본 클래스에서는 정상 플로우 + 로그아웃 후 재사용 차단까지만 끝단에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("Auth E2E 시나리오 (#24)")
class AuthFlowE2ETest {

    private static final String EMAIL = "e2e-user@example.com";
    private static final String RAW_PASSWORD = "P@ssw0rd!2024";
    private static final String NAME = "홍길동";
    private static final String PHONE = "01012345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDb() {
        // refresh_token 이 member 에 FK 를 가지므로 자식부터 삭제한다.
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 → 로그인 → 보호 API → 토큰 회전 → 로그아웃 전체 플로우")
    void fullAuthFlow_succeeds() throws Exception {
        // 1) 회원가입
        Long memberId = signupAndExpectCreated();

        // 2) 로그인 — accessToken / refreshToken 발급
        TokenPair initial = loginAndExtractTokens();

        // 3) 발급된 access 로 보호 API 호출 → 200 + memberId 매칭
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initial.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.role").value("USER"));

        // 4) 토큰 회전 — 새 페어 발급, 응답에 refreshToken 까지 포함되어야 한다 (#22 DoD)
        // access 토큰은 jti 가 없어 같은 1초 내 재발급 시 동일할 수 있으므로 refresh 만 비교한다 (refresh 는 jti UUID 로 unique).
        TokenPair rotated = refreshAndExtractTokens(initial.refresh);
        assertThat(rotated.refresh).isNotEqualTo(initial.refresh);

        // 5) 새 access 로 보호 API 정상 호출
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotated.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId));

        // 6) 로그아웃 — 새 refresh 폐기 (RFC 7009 § 2.2 · 항상 200)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", rotated.refresh))))
                .andExpect(status().isOk());

        // 7) 로그아웃된 refresh 재사용 → 401 (재사용 감지 분기, 토큰 자체는 revoked 상태)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", rotated.refresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Authorization 헤더 누락 시 보호 API 401")
    void protectedApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("형식이 잘못된 Bearer 토큰은 401")
    void protectedApi_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Refresh 회전 후 구 refresh 재사용 시 재사용 감지 — 새 refresh 까지 무효화")
    void rotatedRefresh_reuse_invalidatesAllSessions() throws Exception {
        signupAndExpectCreated();
        TokenPair initial = loginAndExtractTokens();

        TokenPair rotated = refreshAndExtractTokens(initial.refresh);

        // 구 refresh 재사용 → 401 + 같은 사용자의 활성 세션 (= rotated.refresh) 까지 강제 무효화
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", initial.refresh))))
                .andExpect(status().isUnauthorized());

        // 후속: 방금 발급된 새 refresh 도 사용 불가 (재사용 감지가 모든 활성 세션을 폐기)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", rotated.refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃은 형식 오류·미존재 토큰에도 200 (RFC 7009 멱등 처리)")
    void logout_isIdempotent_forInvalidTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "not-a-jwt"))))
                .andExpect(status().isOk());
    }

    private Long signupAndExpectCreated() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", EMAIL,
                "password", RAW_PASSWORD,
                "name", NAME,
                "phone", PHONE
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("memberId").asLong();
    }

    private TokenPair loginAndExtractTokens() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", EMAIL,
                "password", RAW_PASSWORD
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenPair(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private TokenPair refreshAndExtractTokens(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenPair(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private record TokenPair(String access, String refresh) {
    }
}
