package com.groove.auth.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.member.domain.MemberRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #23 — 로그인/회원가입 IP 기반 Rate Limit 통합 테스트.
 *
 * <p>운영 기본값(login=10/min, signup=3/min) 대신 작은 한도를 주입해 한도 초과 직후 429 응답을 검증한다.
 * 각 테스트는 IP 를 다르게 사용해 정책 캐시가 다른 케이스에 영향을 주지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "groove.auth.rate-limit.login.capacity=3",
        "groove.auth.rate-limit.login.refill-period=PT1M",
        "groove.auth.rate-limit.signup.capacity=2",
        "groove.auth.rate-limit.signup.refill-period=PT1M"
})
@DisplayName("Auth Rate Limit (#23)")
class AuthRateLimitIntegrationTest {

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
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인 한도(3회) 초과 → 4번째 요청 429 + Retry-After")
    void login_exceedsCapacity_returns429WithRetryAfter() throws Exception {
        String ip = "203.0.113.10";
        Map<String, String> body = Map.of("email", "limited@example.com", "password", "WrongPass!1234");
        String json = objectMapper.writeValueAsString(body);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginRequest(ip, json))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginRequest(ip, json))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().exists("X-Rate-Limit-Retry-After-Seconds"))
                .andExpect(header().string("X-Rate-Limit-Remaining", "0"))
                .andExpect(jsonPath("$.code").value("SYSTEM_002"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    @Test
    @DisplayName("회원가입 한도(2회) 초과 → 3번째 요청 429 + Retry-After")
    void signup_exceedsCapacity_returns429WithRetryAfter() throws Exception {
        String ip = "203.0.113.20";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(signupRequest(ip, signupBody("user-" + i + "@example.com")))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(signupRequest(ip, signupBody("user-blocked@example.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("SYSTEM_002"));
    }

    @Test
    @DisplayName("서로 다른 IP 는 독립 버킷 — 한쪽이 차단되어도 다른 IP 는 정상")
    void differentIps_haveSeparateBuckets() throws Exception {
        String hot = "203.0.113.30";
        String cool = "203.0.113.31";
        Map<String, String> body = Map.of("email", "iso@example.com", "password", "WrongPass!1234");
        String json = objectMapper.writeValueAsString(body);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(loginRequest(hot, json))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(loginRequest(hot, json))
                .andExpect(status().isTooManyRequests());

        // 다른 IP 는 영향 없음 — 인증 실패(401) 가 정상 흐름
        mockMvc.perform(loginRequest(cool, json))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder loginRequest(String ip, String body) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    private MockHttpServletRequestBuilder signupRequest(String ip, String body) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    private String signupBody(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", "P@ssw0rd!2024",
                "name", "홍길동",
                "phone", "01012345678"
        ));
    }
}
