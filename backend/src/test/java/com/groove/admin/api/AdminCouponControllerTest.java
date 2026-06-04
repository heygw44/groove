package com.groove.admin.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.support.CouponFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/admin/coupons (ADMIN 쿠폰 CRUD · 직접지급)")
class AdminCouponControllerTest {

    private static final Instant VALID_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2026-12-31T00:00:00Z");

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

    private String adminBearer;
    private String userBearer;
    private Long activeMemberId;
    private Long withdrawnMemberId;

    @BeforeEach
    void setUp() {
        clearAll();

        Member active = memberRepository.saveAndFlush(
                Member.register("active@example.com", "$2a$10$dummy", "Active", "01000000001"));
        Member withdrawn = memberRepository.saveAndFlush(
                Member.register("withdrawn@example.com", "$2a$10$dummy", "Withdrawn", "01000000002"));
        withdrawn.withdraw(Instant.now());
        memberRepository.saveAndFlush(withdrawn);

        activeMemberId = active.getId();
        withdrawnMemberId = withdrawn.getId();

        adminBearer = "Bearer " + jwtProvider.issueAccessToken(999L, MemberRole.ADMIN);
        userBearer = "Bearer " + jwtProvider.issueAccessToken(activeMemberId, MemberRole.USER);
    }

    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        refreshTokenRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private Coupon persistCoupon() {
        return couponRepository.saveAndFlush(CouponFixtures.fixedAmount(null));
    }

    private Coupon persistCoupon(CouponStatus status) {
        Coupon c = couponRepository.saveAndFlush(CouponFixtures.fixedAmount(null));
        if (status != CouponStatus.ACTIVE) {
            c.changeStatus(status);
            couponRepository.saveAndFlush(c);
        }
        return c;
    }

    // ---------------------------------------------------------------------
    // 인가
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("POST 생성 미인증 → 401")
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST 생성 USER 권한 → 403 + 쿠폰 미생성")
    void create_userRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
        assertThat(couponRepository.count()).isZero();
    }

    @Test
    @DisplayName("GET 목록 USER 권한 → 403")
    void list_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH 상태 USER 권한 → 403 + 상태 불변")
    void status_userRole_returns403() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/status", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
        assertThat(couponRepository.findById(c.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("POST 직접지급 USER 권한 → 403 + member_coupon 무변화")
    void grant_userRole_returns403() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isForbidden());
        assertThat(memberCouponRepository.count()).isZero();
    }

    // ---------------------------------------------------------------------
    // 생성
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("POST 생성 유효 정액 → 201 + DB 저장")
    void create_validFixed_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").exists())
                .andExpect(jsonPath("$.discountType").value("FIXED_AMOUNT"))
                .andExpect(jsonPath("$.discountValue").value(1000))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.issuedCount").value(0));
        assertThat(couponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST 생성 정률 + totalQuantity null → 201 (무제한 직접지급용)")
    void create_validPercentageUnlimited_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "정률 10%",
                "discountType", "PERCENTAGE",
                "discountValue", 10,
                "maxDiscountAmount", 5000,
                "minOrderAmount", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_FROM.toString(),
                "validUntil", VALID_UNTIL.toString()));
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.maxDiscountAmount").value(5000));
    }

    @Test
    @DisplayName("POST 생성 정률 200 → 400 VALIDATION_FAILED (도메인 빌더가 1~100 검증)")
    void create_percentageOutOfRange_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "정률 200%",
                "discountType", "PERCENTAGE",
                "discountValue", 200,
                "minOrderAmount", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_FROM.toString(),
                "validUntil", VALID_UNTIL.toString()));
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("POST 생성 validUntil ≤ validFrom → 400 (도메인 검증)")
    void create_invalidPeriod_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "x",
                "discountType", "FIXED_AMOUNT",
                "discountValue", 1000,
                "minOrderAmount", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_UNTIL.toString(),
                "validUntil", VALID_FROM.toString()));
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 생성 정률 + maxDiscountAmount 누락 → 400 (도메인 가드: 정률은 상한 필수)")
    void create_percentageWithoutCap_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "정률 10% 무제한",
                "discountType", "PERCENTAGE",
                "discountValue", 10,
                "minOrderAmount", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_FROM.toString(),
                "validUntil", VALID_UNTIL.toString()));
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("POST 생성 totalQuantity=0 → 400 (도메인 가드: 죽은 정책 차단)")
    void create_totalQuantityZero_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "죽은 정책",
                "discountType", "FIXED_AMOUNT",
                "discountValue", 1000,
                "minOrderAmount", 0,
                "totalQuantity", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_FROM.toString(),
                "validUntil", VALID_UNTIL.toString()));
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_001"));
    }

    @Test
    @DisplayName("POST 생성 name 누락 → 400 (Bean Validation)")
    void create_missingName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "discountType", "FIXED_AMOUNT",
                "discountValue", 1000,
                "minOrderAmount", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_FROM.toString(),
                "validUntil", VALID_UNTIL.toString()));
        mockMvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // 목록
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET 목록 → 200 페이징")
    void list_returnsAll() throws Exception {
        persistCoupon();
        persistCoupon();
        mockMvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET 목록 ?status=SUSPENDED → 해당 상태만")
    void list_filterByStatus() throws Exception {
        persistCoupon(CouponStatus.ACTIVE);
        Coupon suspended = persistCoupon(CouponStatus.SUSPENDED);
        mockMvc.perform(get("/api/v1/admin/coupons").param("status", "SUSPENDED")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].couponId").value(suspended.getId()));
    }

    @Test
    @DisplayName("GET 목록 허용되지 않은 정렬 키 → 400")
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/coupons").param("sort", "name,desc")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // 상태 변경
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("PATCH 상태 합법 전이(ACTIVE→SUSPENDED) → 200 + DB 반영")
    void status_legalTransition_returns200() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/status", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(couponRepository.findById(c.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.SUSPENDED);
    }

    @Test
    @DisplayName("PATCH 상태 불법 전이(ENDED→ACTIVE) → 409 COUPON_INVALID_STATE_TRANSITION + 불변")
    void status_illegalTransition_returns409() throws Exception {
        Coupon c = persistCoupon(CouponStatus.ENDED);
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/status", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID_STATE_TRANSITION"));
        assertThat(couponRepository.findById(c.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.ENDED);
    }

    @Test
    @DisplayName("PATCH 상태 self-transition (ACTIVE→ACTIVE) → 200 멱등 처리, 상태 그대로")
    void status_selfTransition_isIdempotent() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/status", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(couponRepository.findById(c.getId()).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("PATCH 상태 target 누락 → 400")
    void status_missingTarget_returns400() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/status", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH 상태 미존재 쿠폰 → 404")
    void status_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/status", 99999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------
    // 직접지급
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("POST 직접지급 활성 회원 → 201 + AdminMemberCouponResponse + issuedCount 비증가")
    void grant_activeMember_returns201() throws Exception {
        Coupon c = couponRepository.saveAndFlush(CouponFixtures.fixedAmount(1));
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCouponId").exists())
                .andExpect(jsonPath("$.couponId").value(c.getId()))
                .andExpect(jsonPath("$.memberId").value(activeMemberId))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.issuedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists());
        assertThat(memberCouponRepository.count()).isEqualTo(1);
        MemberCoupon mc = memberCouponRepository.findAll().get(0);
        assertThat(mc.getMemberId()).isEqualTo(activeMemberId);
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        // 직접지급은 정책 카운터를 증가시키지 않는다 — totalQuantity=1 인 쿠폰에 1번 grant 했어도 issuedCount=0.
        assertThat(couponRepository.findById(c.getId()).orElseThrow().getIssuedCount()).isZero();
    }

    @Test
    @DisplayName("POST 직접지급 SUSPENDED 쿠폰 → 422 COUPON_NOT_ISSUABLE + 발급 안 됨")
    void grant_suspendedCoupon_returns422() throws Exception {
        Coupon c = persistCoupon(CouponStatus.SUSPENDED);
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_ISSUABLE"));
        assertThat(memberCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST 직접지급 ENDED 쿠폰 → 422 COUPON_NOT_ISSUABLE")
    void grant_endedCoupon_returns422() throws Exception {
        Coupon c = persistCoupon(CouponStatus.ENDED);
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_ISSUABLE"));
        assertThat(memberCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST 직접지급 — totalQuantity=1 인 쿠폰에 두 회원 지급 모두 성공 (한정수량과 독립)")
    void grant_independentFromTotalQuantity() throws Exception {
        Coupon c = couponRepository.saveAndFlush(CouponFixtures.fixedAmount(1));
        Member another = memberRepository.saveAndFlush(
                Member.register("another@example.com", "$2a$10$dummy", "Another", "01000000099"));

        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + another.getId() + "}"))
                .andExpect(status().isCreated());
        assertThat(memberCouponRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("POST 직접지급 동일 회원에 중복 지급 → 409 COUPON_ALREADY_ISSUED")
    void grant_duplicate_returns409() throws Exception {
        Coupon c = persistCoupon();
        memberCouponRepository.saveAndFlush(MemberCoupon.issue(c, activeMemberId));

        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_ISSUED"));
        assertThat(memberCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST 직접지급 탈퇴 회원 → 404 MEMBER_NOT_FOUND")
    void grant_withdrawnMember_returns404() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + withdrawnMemberId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        assertThat(memberCouponRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST 직접지급 없는 회원 → 404 MEMBER_NOT_FOUND")
    void grant_missingMember_returns404() throws Exception {
        Coupon c = persistCoupon();
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", c.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":99999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST 직접지급 없는 쿠폰 → 404 COUPON_NOT_FOUND")
    void grant_missingCoupon_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/coupons/{id}/grant", 99999L)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + activeMemberId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    private String validCreateBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", "신규가입 쿠폰",
                "discountType", "FIXED_AMOUNT",
                "discountValue", 1000,
                "minOrderAmount", 0,
                "perMemberLimit", 1,
                "validFrom", VALID_FROM.toString(),
                "validUntil", VALID_UNTIL.toString()));
    }
}
