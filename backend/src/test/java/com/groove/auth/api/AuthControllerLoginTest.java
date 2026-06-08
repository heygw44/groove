package com.groove.auth.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.member.domain.Member;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("POST /api/v1/auth/login & /logout")
class AuthControllerLoginTest {

    private static final String EMAIL = "login-user@example.com";
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
        // refresh_token 이 member 에 FK 를 가지므로 자식부터 삭제한다.
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAll();
        Member member = Member.register(
                EMAIL,
                passwordEncoder.encode(RAW_PASSWORD),
                "홍길동",
                "01012345678"
        );
        memberRepository.saveAndFlush(member);
    }

    @Test
    @DisplayName("정상 로그인 → 200 + accessToken body + refresh 토큰 HttpOnly 쿠키 (#163)")
    void login_success_returnsTokens() throws Exception {
        Map<String, String> body = Map.of("email", EMAIL, "password", RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                // refresh 토큰은 body 에 노출하지 않고 HttpOnly 쿠키로만 내려간다 (#163)
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().value("refreshToken", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())));
    }

    @Test
    @DisplayName("잘못된 비밀번호 → 401 AUTH_INVALID_CREDENTIALS")
    void login_wrongPassword_returns401() throws Exception {
        Map<String, String> body = Map.of("email", EMAIL, "password", "WrongPass!1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("미존재 이메일 → 401 AUTH_INVALID_CREDENTIALS (사용자 열거 방지)")
    void login_unknownEmail_returnsSameCode() throws Exception {
        Map<String, String> body = Map.of("email", "ghost@example.com", "password", RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("이메일 형식 오류 → 400 VALID_001")
    void login_invalidEmailFormat_returns400() throws Exception {
        Map<String, String> body = Map.of("email", "not-email", "password", RAW_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비밀번호 누락 → 400")
    void login_missingPassword_returns400() throws Exception {
        Map<String, String> body = Map.of("email", EMAIL, "password", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그아웃 → 200 + refresh 쿠키 삭제(Max-Age=0) (#163)")
    void logout_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "any.refresh.token")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));
    }

    @Test
    @DisplayName("로그아웃 refresh 쿠키 없음 → 200 (멱등, #163)")
    void logout_missingCookie_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());
    }
}
