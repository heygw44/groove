package com.groove.auth.api;

import com.groove.auth.domain.RefreshToken;
import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.domain.TokenHasher;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh Token Rotation 통합 테스트 (#22 DoD 검증).
 *
 * <p>실제 MySQL(Testcontainers) + Spring Boot 컨텍스트로 다음을 검증한다:
 * <ul>
 *   <li>정상 회전: 새 access·refresh 페어 발급, 기존 refresh 는 revoke + replaced_by 연결</li>
 *   <li>회전된 (revoked) refresh 재사용 → 401 + 같은 사용자 활성 토큰 전체 무효화</li>
 *   <li>잘못된 형식 토큰 → 401</li>
 *   <li>로그아웃 후 동일 refresh 재사용 → 401 (재사용 감지로 전체 무효화)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("POST /api/v1/auth/refresh — Rotation/재사용 감지 통합")
class AuthControllerRefreshTest {

    private static final String EMAIL = "rotate-user@example.com";
    private static final String RAW_PASSWORD = "P@ssw0rd!2024";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAll();
        Member member = Member.register(
                EMAIL, passwordEncoder.encode(RAW_PASSWORD), "회전", "01099998888");
        memberRepository.saveAndFlush(member);
    }

    @Test
    @DisplayName("정상 rotation: 새 access·refresh 발급 + 기존 토큰 revoke + replaced_by 연결")
    void refresh_success_rotatesPairAndLinksChain() throws Exception {
        String oldRefresh = login();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String newRefresh = body.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        RefreshToken oldRow = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(oldRefresh)).orElseThrow();
        RefreshToken newRow = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(newRefresh)).orElseThrow();

        assertThat(oldRow.isRevoked()).as("기존 refresh 는 revoke 되어야 함").isTrue();
        assertThat(oldRow.getReplacedByTokenId())
                .as("회전 체인에 새 토큰 id 가 연결되어야 함")
                .isEqualTo(newRow.getId());
        assertThat(newRow.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("revoked refresh 재사용 → 401 + 같은 사용자의 다른 활성 refresh 도 모두 무효화")
    void refresh_reusedRevokedToken_revokesAllUserSessions() throws Exception {
        String firstRefresh = login();
        // 세션 2 — 다른 디바이스에서 별도 로그인
        String secondRefresh = login();

        // 세션 1 회전 → firstRefresh 가 revoked 상태가 됨
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isOk());

        // 공격자가 가로챈 firstRefresh 를 다시 사용 시도 → 재사용 감지
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        // DoD §2: 같은 member 의 다른 활성 refresh(secondRefresh) 도 무효화되어야 함
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        Long memberId = memberRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        List<RefreshToken> all = refreshTokenRepository.findAll();
        assertThat(all)
                .filteredOn(t -> t.getMemberId().equals(memberId))
                .as("회원의 토큰이 최소 1건 이상 존재해야 의미 있는 검증")
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t.isRevoked())
                        .as("재사용 감지 후 해당 사용자의 모든 토큰은 revoked")
                        .isTrue());
    }

    @Test
    @DisplayName("잘못된 형식의 refresh → 401 AUTH_INVALID_TOKEN")
    void refresh_malformedToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "garbage"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("refreshToken 누락 → 400 ProblemDetail")
    void refresh_missingToken_returns400() throws Exception {
        // 검증 실패는 별도 ExceptionHandler 가 없어 ProblemDetailEnricher 의 fallback "HTTP_400" 사용.
        // 본 PR 범위 외이므로 현재 동작을 그대로 단언해 회귀 감지만 보장한다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("HTTP_400"));
    }

    @Test
    @DisplayName("로그아웃 후 동일 토큰 refresh → 재사용 감지로 401 + 다른 활성 세션도 모두 무효화")
    void logout_thenRefresh_triggersReuseDetectionAndRevokesAllSessions() throws Exception {
        String loggedOutRefresh = login();
        // 로그아웃되지 않은 다른 디바이스 세션. 재사용 감지로 함께 무효화되어야 함.
        String otherActiveRefresh = login();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loggedOutRefresh))))
                .andExpect(status().isOk());

        RefreshToken loggedOutRow = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(loggedOutRefresh)).orElseThrow();
        assertThat(loggedOutRow.isRevoked()).isTrue();

        // 폐기된 토큰을 도난당한 공격자가 재사용 시도 → 재사용 감지 분기
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loggedOutRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        // DoD §2: 같은 사용자의 다른 활성 세션(otherActiveRefresh)도 무효화되어야 함
        RefreshToken otherRow = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(otherActiveRefresh)).orElseThrow();
        assertThat(otherRow.isRevoked())
                .as("재사용 감지 후 같은 사용자의 다른 활성 토큰도 강제 무효화되어야 함")
                .isTrue();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", otherActiveRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("로그아웃: 잘못된 형식 토큰 → 200 (RFC 7009 멱등)")
    void logout_invalidToken_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "not-a-jwt"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃: 빈 문자열 refreshToken → 200 (invalid token, RFC 7009 § 2.2)")
    void logout_emptyStringRefreshToken_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", ""))))
                .andExpect(status().isOk());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("refreshToken").asText();
    }
}
