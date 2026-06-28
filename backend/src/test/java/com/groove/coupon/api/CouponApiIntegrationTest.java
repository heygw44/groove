package com.groove.coupon.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.support.OrderFixtures;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
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
 * 쿠폰 API 통합 테스트. Testcontainers MySQL 위 MockMvc 로 실 필터(@Idempotent 인터셉터)·서비스·DB 를 거쳐
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
    private OrderRepository orderRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Long ownerId;
    private String ownerBearer;
    private String otherBearer;

    @BeforeEach
    void setUp() {
        // FK 삭제 순서: member_coupon → coupon(RESTRICT)·member(CASCADE)·orders(SET NULL).
        refreshTokenRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        ownerId = memberRepository.saveAndFlush(
                MemberFixtures.register("owner@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Owner", "01000000001")).getId();
        Long otherMemberId = memberRepository.saveAndFlush(
                MemberFixtures.register("other@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Other", "01000000002")).getId();

        ownerBearer = "Bearer " + jwtProvider.issueAccessToken(ownerId, MemberRole.USER);
        otherBearer = "Bearer " + jwtProvider.issueAccessToken(otherMemberId, MemberRole.USER);
    }

    /** 멱등성 키 — 매 요청 고유 UUID. */
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
    @DisplayName("GET /members/me/coupons (USER) → 200, 본인 보유 쿠폰")
    void listMine_returnsOwnerCoupons() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);
        memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, ownerId, NOW));

        mockMvc.perform(get("/api/v1/members/me/coupons")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].couponId").value(coupon.getId().intValue()))
                .andExpect(jsonPath("$.content[0].status").value("ISSUED"));
    }

    @Test
    @DisplayName("GET /members/me/coupons?status=USED → 사용 주문의 orderNumber 가 채워진다 (#137)")
    void listMine_usedCoupon_resolvesOrderNumber() throws Exception {
        Coupon coupon = persistCoupon(100, CouponStatus.ACTIVE);
        Order order = orderRepository.saveAndFlush(OrderFixtures.memberOrder("ORD-20260604-000137", ownerId));
        MemberCoupon used = MemberCoupon.issue(coupon, ownerId, NOW);
        used.use(order.getId(), NOW);
        memberCouponRepository.saveAndFlush(used);

        mockMvc.perform(get("/api/v1/members/me/coupons")
                        .param("status", "USED")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("USED"))
                .andExpect(jsonPath("$.content[0].orderNumber").value(order.getOrderNumber()));
    }

    @Test
    @DisplayName("GET /members/me/coupons → 혼재 페이지에서 orderNumber 가 행별로 매핑된다 (#137 회귀)")
    void listMine_mixedPage_mapsOrderNumberPerRow() throws Exception {
        // USED 쿠폰 1장(주문 연결) + 미사용 ISSUED 쿠폰 1장을 같은 페이지에 둔다.
        Coupon usedCoupon = persistCoupon(100, CouponStatus.ACTIVE);
        Order order = orderRepository.saveAndFlush(OrderFixtures.memberOrder("ORD-20260604-000137", ownerId));
        MemberCoupon used = MemberCoupon.issue(usedCoupon, ownerId, NOW);
        used.use(order.getId(), NOW);
        memberCouponRepository.saveAndFlush(used);

        Coupon issuedCoupon = persistCoupon(100, CouponStatus.ACTIVE);
        memberCouponRepository.saveAndFlush(MemberCoupon.issue(issuedCoupon, ownerId, NOW));

        MvcResult result = mockMvc.perform(get("/api/v1/members/me/coupons")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        JsonNode usedRow = contentByStatus(content, "USED");
        JsonNode issuedRow = contentByStatus(content, "ISSUED");

        // USED 행에만 주문번호가 붙고, ISSUED 행은 키는 있되 명시적 null.
        assertThat(usedRow.get("orderNumber").asString()).isEqualTo(order.getOrderNumber());
        assertThat(issuedRow.has("orderNumber")).isTrue();
        assertThat(issuedRow.get("orderNumber").isNull()).isTrue();
    }

    /** content 배열에서 주어진 status 의 첫 행을 찾는다 — 없으면 단언 실패. */
    private JsonNode contentByStatus(JsonNode content, String status) {
        for (JsonNode row : content) {
            if (status.equals(row.get("status").asString())) {
                return row;
            }
        }
        throw new AssertionError("status=" + status + " 행이 응답에 없음");
    }

    @Test
    @DisplayName("GET /members/me/coupons 미인증 → 401")
    void listMine_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/coupons"))
                .andExpect(status().isUnauthorized());
    }
}
