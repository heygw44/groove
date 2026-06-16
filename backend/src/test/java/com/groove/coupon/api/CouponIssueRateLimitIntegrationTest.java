package com.groove.coupon.api;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 선착순 발급 Rate Limit 통합 테스트 — 회원당 한도 초과 시 429, 회원 간 버킷 독립.
 *
 * <p>capacity=3 으로 override 한다. 미존재 쿠폰 id 로 호출하면 토큰 소진 전까지는 404·소진 후 429 다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "groove.coupon.rate-limit.issue.capacity=3")
@DisplayName("POST /coupons/{id}/issue Rate Limit (#90)")
class CouponIssueRateLimitIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final long UNKNOWN_COUPON_ID = 999_999L;

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
                MemberFixtures.register("rl-owner@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Owner", "01000000001")).getId();
        Long otherId = memberRepository.saveAndFlush(
                MemberFixtures.register("rl-other@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Other", "01000000002")).getId();
        ownerBearer = "Bearer " + jwtProvider.issueAccessToken(ownerId, MemberRole.USER);
        otherBearer = "Bearer " + jwtProvider.issueAccessToken(otherId, MemberRole.USER);
    }

    @Test
    @DisplayName("회원당 capacity(3) 초과 → 4번째 429, 다른 회원 버킷은 독립")
    void perMemberLimit_exceeded_returns429() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/coupons/{id}/issue", UNKNOWN_COUPON_ID)
                            .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                            .header(IDEMPOTENCY_HEADER, java.util.UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", UNKNOWN_COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, java.util.UUID.randomUUID().toString()))
                .andExpect(status().isTooManyRequests());

        // 다른 회원은 자신의 버킷을 가지므로 한도와 무관하게 통과(404).
        mockMvc.perform(post("/api/v1/coupons/{id}/issue", UNKNOWN_COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .header(IDEMPOTENCY_HEADER, java.util.UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
