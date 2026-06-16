package com.groove.payment.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.support.MemberFixtures;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 요청 Rate Limit 통합 테스트 — 회원당 한도 초과 시 429, 회원 간 버킷 독립.
 *
 * <p>capacity=3 으로 override. 미존재 주문번호로 호출해 토큰 소진 전까지는 404·소진 후 429 로 한도를 관찰한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "groove.payment.rate-limit.post.capacity=3")
@DisplayName("POST /payments Rate Limit")
class PaymentRateLimitIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String BODY = "{\"orderNumber\":\"ORD-20260101-AAAAAA\",\"method\":\"CARD\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String ownerBearer;
    private String otherBearer;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Long ownerId = memberRepository.saveAndFlush(
                MemberFixtures.register("pay-rl-owner@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Owner", "01000000001")).getId();
        Long otherId = memberRepository.saveAndFlush(
                MemberFixtures.register("pay-rl-other@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Other", "01000000002")).getId();
        ownerBearer = "Bearer " + jwtProvider.issueAccessToken(ownerId, MemberRole.USER);
        otherBearer = "Bearer " + jwtProvider.issueAccessToken(otherId, MemberRole.USER);
    }

    @Test
    @DisplayName("회원당 capacity(3) 초과 → 4번째 429, 다른 회원 버킷은 독립")
    void perMemberLimit_exceeded_returns429() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/payments")
                            .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                            .header(IDEMPOTENCY_HEADER, java.util.UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests());

        // 다른 회원은 자신의 버킷을 가져 통과(404).
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .header(IDEMPOTENCY_HEADER, java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }
}
