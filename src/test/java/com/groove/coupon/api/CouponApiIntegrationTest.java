package com.groove.coupon.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠폰 API 통합 테스트 (#90, API.md §3.9).
 *
 * <p>Testcontainers MySQL 위 MockMvc 로 실 필터(@Idempotent 인터셉터)·서비스·DB 를 모두 거친다.
 * 발급 동시성(초과발급 없음) 자체는 {@code CouponIssuanceConcurrencyTest} 가 검증하고, 본 테스트는
 * 발급/조회의 HTTP 계약(상태코드·에러코드·멱등 replay·인증)을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/coupons 발급·조회 API (#90)")
class CouponApiIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final Instant NOW = Instant.now();
    private static final Instant VALID_FROM = NOW.minus(1, ChronoUnit.DAYS);
    private static final Instant VALID_UNTIL = NOW.plus(10, ChronoUnit.DAYS);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberCouponRepository memberCouponRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Long ownerId;
    private String ownerBearer;
    private String otherBearer;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: member_coupon → coupon, member. refresh_token → member.
        refreshTokenRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        ownerId = memberRepository.saveAndFlush(
                Member.register("owner@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Owner", "01000000001")).getId();
        Long otherMemberId = memberRepository.saveAndFlush(
                Member.register("other@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Other", "01000000002")).getId();

        ownerBearer = "Bearer " + jwtProvider.issueAccessToken(ownerId, MemberRole.USER);
        otherBearer = "Bearer " + jwtProvider.issueAccessToken(otherMemberId, MemberRole.USER);
    }

    /** 멱등성 키 — Testcontainers 재사용으로 idempotency_record 가 빌드 간 살아남으므로 매 요청 고유 UUID 를 쓴다. */
    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private Coupon persistCoupon(Integer totalQuantity, CouponStatus status) {
        Coupon coupon = Coupon.builder("정액 3천원", CouponDiscountType.FIXED_AMOUNT, 3_000, VALID_FROM, VALID_UNTIL)
                .totalQuantity(totalQuantity)
                .build();
        if (status != CouponStatus.ACTIVE) {
            coupon.changeStatus(status);
        }
        return couponRepository.saveAndFlush(coupon);
    }

    @Test
    @DisplayName("발급 성공 → 201 + memberCouponId·ISSUED, member_coupon 1건, issued_count 1")
    void issue_success_returns201() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCouponId").value(notNullValue()))
                .andExpect(jsonPath("$.couponId").value(coupon.getId().intValue()))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.discountType").value("FIXED_AMOUNT"));

        assertThat(memberCouponRepository.count()).isEqualTo(1);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency-Key 헤더 누락 → 400 IDEMPOTENCY_KEY_REQUIRED, 미발급")
    void issue_missingIdempotencyKey_returns400() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(memberCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("동일 Idempotency-Key 재요청 → 최초 응답 replay, member_coupon 1건")
    void issue_sameIdempotencyKey_replays() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);
        String replayKey = newKey();

        MvcResult first = mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, replayKey))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("memberCouponId").asLong();

        MvcResult second = mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, replayKey))
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("memberCouponId").asLong();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(memberCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("소진 쿠폰(total=1, 이미 1장 발급) → 409 COUPON_SOLD_OUT")
    void issue_soldOut_returns409() throws Exception {
        Coupon coupon = persistCoupon(1, CouponStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_SOLD_OUT"));

        assertThat(memberCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 발급받은 회원의 재발급(다른 키) → 409 COUPON_ALREADY_ISSUED, 1장 유지")
    void issue_alreadyIssued_returns409() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_ISSUED"));

        assertThat(memberCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SUSPENDED 쿠폰 발급 → 422 COUPON_NOT_ISSUABLE")
    void issue_suspended_returns422() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.SUSPENDED);

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_ISSUABLE"));

        assertThat(memberCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("미존재 쿠폰 발급 → 404 COUPON_NOT_FOUND")
    void issue_unknownCoupon_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/{id}/issue", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("미인증 발급 → 401")
    void issue_unauthenticated_returns401() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/coupons/{id}/issue", coupon.getId())
                        .header(IDEMPOTENCY_HEADER, newKey()))
                .andExpect(status().isUnauthorized());

        assertThat(memberCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("GET /coupons (Public) → 200, ACTIVE·기간 내만 노출 + remainingQuantity")
    void listIssuable_public_returnsActiveOnly() throws Exception {
        Coupon active = persistCoupon(100, CouponStatus.ACTIVE);
        persistCoupon(100, CouponStatus.SUSPENDED);

        mockMvc.perform(get("/api/v1/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].couponId").value(active.getId().intValue()))
                .andExpect(jsonPath("$.content[0].remainingQuantity").value(100));
    }

    @Test
    @DisplayName("GET /me/coupons (USER) → 200, 본인 보유 쿠폰")
    void listMine_returnsOwnerCoupons() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);
        memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, ownerId));

        mockMvc.perform(get("/api/v1/me/coupons")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].couponId").value(coupon.getId().intValue()))
                .andExpect(jsonPath("$.content[0].status").value("ISSUED"));
    }

    @Test
    @DisplayName("GET /me/coupons 미인증 → 401")
    void listMine_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/coupons"))
                .andExpect(status().isUnauthorized());
    }
}
