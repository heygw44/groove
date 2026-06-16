package com.groove.auth.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.auth.security.RefreshTokenCookieFactory;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 도메인 E2E 시나리오 — 회원가입 → 로그인 → 보호 API → 토큰 회전 → 로그아웃을
 * 실 필터·서비스·DB(Testcontainers)로 검증한다. 보호 엔드포인트는 TestSecuredController(test 프로파일).
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
    /** refresh 토큰 HttpOnly 쿠키 이름. */
    private static final String REFRESH_COOKIE = RefreshTokenCookieFactory.COOKIE_NAME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @BeforeEach
    void cleanDb() {
        // 자식(refresh_token)부터 삭제한다.
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
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

        // 4) 토큰 회전 — 새 페어 발급
        // access 는 jti 가 없어 같은 1초 내 동일할 수 있으므로 refresh(jti UUID)만 비교한다.
        TokenPair rotated = refreshAndExtractTokens(initial.refresh);
        assertThat(rotated.refresh).isNotEqualTo(initial.refresh);

        // 5) 새 access 로 보호 API 정상 호출
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rotated.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId));

        // 6) 로그아웃 — 새 refresh 폐기 (항상 200)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(rotated.refresh)))
                .andExpect(status().isOk());

        // 7) 로그아웃으로 revoked 된 refresh 재사용 시도 → 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(rotated.refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Authorization 헤더 누락 시 보호 API 401 + AUTH ProblemDetail")
    void protectedApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("AUTH_")));
    }

    @Test
    @DisplayName("형식이 잘못된 Bearer 토큰은 401 + AUTH ProblemDetail")
    void protectedApi_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("AUTH_")));
    }

    @Test
    @DisplayName("Refresh 회전 후 구 refresh 재사용 시 재사용 감지 — 새 refresh 까지 무효화")
    void rotatedRefresh_reuse_invalidatesAllSessions() throws Exception {
        signupAndExpectCreated();
        TokenPair initial = loginAndExtractTokens();

        TokenPair rotated = refreshAndExtractTokens(initial.refresh);

        // 구 refresh 재사용 → 401 + 활성 세션(= rotated.refresh)까지 무효화
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(initial.refresh)))
                .andExpect(status().isUnauthorized());

        // 방금 발급된 새 refresh 도 사용 불가
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(rotated.refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃은 형식 오류·미존재 토큰에도 200 (RFC 7009 멱등 처리)")
    void logout_isIdempotent_forInvalidTokens() throws Exception {
        // (a) 형식 오류 토큰 → 멱등 무동작
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie("not-a-jwt")))
                .andExpect(status().isOk());

        // (b) well-formed JWT 형식이지만 DB 에 미존재
        String orphanRefreshToken = jwtProvider.issueRefreshToken(99_999L);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(orphanRefreshToken)))
                .andExpect(status().isOk());

        // (c) 쿠키 자체가 없는 경우에도 멱등 200
        mockMvc.perform(post("/api/v1/auth/logout"))
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
                .andExpect(jsonPath("$.memberId").isNumber())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return requireField(json, "memberId").asLong();
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
                .andExpect(jsonPath("$.accessToken").isString())
                // refresh 토큰은 body 가 아닌 HttpOnly 쿠키로 내려간다
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        return extractTokenPair(result);
    }

    private TokenPair refreshAndExtractTokens(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        return extractTokenPair(result);
    }

    private TokenPair extractTokenPair(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenPair(
                requireField(json, "accessToken").asText(),
                extractRefreshCookie(result)
        );
    }

    /** 응답 Set-Cookie 에서 회전된 refresh 토큰을 추출한다. */
    private String extractRefreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).as("refresh 토큰은 HttpOnly 쿠키로 내려가야 함").isNotNull();
        assertThat(cookie.isHttpOnly()).as("refresh 쿠키는 HttpOnly 여야 함").isTrue();
        return cookie.getValue();
    }

    /** 요청에 refresh 토큰 쿠키를 실어 보낸다. */
    private Cookie refreshCookie(String token) {
        return new Cookie(REFRESH_COOKIE, token);
    }

    /** 응답 JSON 에서 필드를 추출한다. 누락 시 명확한 메시지로 실패한다. */
    private static JsonNode requireField(JsonNode json, String field) {
        return Objects.requireNonNull(json.get(field), () -> "응답 JSON 에 '" + field + "' 필드가 없음: " + json);
    }

    private record TokenPair(String access, String refresh) {
    }
}
