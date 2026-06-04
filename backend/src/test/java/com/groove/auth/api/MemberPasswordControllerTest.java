package com.groove.auth.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/members/me/password 비밀번호 변경 API 통합 테스트 (#77).
 *
 * <p>실 필터·서비스·DB(Testcontainers)를 거쳐 DoD 를 검증한다 — 정상 변경 후 새 비번 로그인 가능·기존
 * 비번 거부, 현재 비번 불일치, {@code new == current}, 약한 비번, 세션 무효화, 미인증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/members/me/password 비밀번호 변경 API")
class MemberPasswordControllerTest {

    private static final String EMAIL = "pwchange@example.com";
    private static final String OLD_PASSWORD = "P@ssw0rd!2024";
    private static final String NEW_PASSWORD = "NewP@ss!98765";

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userBearer;
    private Long memberId;

    @BeforeEach
    void setUp() {
        // refresh_token 이 member 에 FK(비-cascade)를 가지므로 자식부터 삭제한다.
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                Member.register(EMAIL, passwordEncoder.encode(OLD_PASSWORD), "김철수", "01012345678"));
        memberId = member.getId();
        userBearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    @Test
    @DisplayName("정상 변경 → 204, 새 비번으로 로그인 성공·기존 비번 거부")
    void changePassword_success_returns204AndSwapsCredentials() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // 기존 비번 로그인 → 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(OLD_PASSWORD)))
                .andExpect(status().isUnauthorized());

        // 새 비번 로그인 → 200
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    @DisplayName("현재 비번 불일치 → 400 MEMBER_PASSWORD_MISMATCH, DB 비번 미변경")
    void changePassword_wrongCurrent_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody("WrongCurrent!99", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_PASSWORD_MISMATCH"));

        Member unchanged = memberRepository.findById(memberId).orElseThrow();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, unchanged.getPassword())).isTrue();
    }

    @Test
    @DisplayName("새 비번 == 현재 비번 → 400 (검증 거부)")
    void changePassword_sameAsCurrent_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(OLD_PASSWORD, OLD_PASSWORD)))
                .andExpect(status().isBadRequest())
                // 본문 검증(@AssertTrue) 거부는 code=VALID_001 + violations — 서비스의 MEMBER_PASSWORD_MISMATCH 와 구분된다.
                .andExpect(jsonPath("$.code").value("VALID_001"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("새 비번이 정책 위반(약함/짧음) → 400 (검증 거부)")
    void changePassword_weakNewPassword_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(OLD_PASSWORD, "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"))
                .andExpect(jsonPath("$.violations[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("변경 후 기존 refresh 토큰 무효화 → 회전 시 401")
    void changePassword_revokesExistingRefreshTokens() throws Exception {
        // 로그인으로 실제 refresh 토큰을 DB 에 발급
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(OLD_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(login.getResponse().getContentAsString());
        String refreshToken = tokens.get("refreshToken").asText();
        String accessToken = tokens.get("accessToken").asText();

        // 비밀번호 변경 (로그인으로 받은 access 사용)
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        // 변경 전 발급된 refresh 토큰으로 회전 시도 → 401 (전부 무효화됨)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("미인증 → 401")
    void changePassword_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(OLD_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private String changeBody(String current, String next) {
        return objectMapper.writeValueAsString(Map.of(
                "currentPassword", current,
                "newPassword", next
        ));
    }

    private String loginBody(String password) {
        return objectMapper.writeValueAsString(Map.of(
                "email", EMAIL,
                "password", password
        ));
    }
}
