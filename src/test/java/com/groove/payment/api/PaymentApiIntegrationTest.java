package com.groove.payment.api;

import com.groove.auth.security.JwtProvider;
import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 요청·조회 API 통합 테스트 (#55).
 *
 * <p>Testcontainers MySQL 위에서 부팅된 MockMvc 로 실 필터({@code @Idempotent} 인터셉터 포함)·서비스·DB
 * 를 모두 거친다. {@code test} 프로파일이라 {@code MockPaymentGateway} 가 활성이며 — {@code request()} 시
 * 즉시 PENDING 으로 응답한다. {@code payment.mock.auto-webhook=false} 로 인프로세스 자동 웹훅을 꺼,
 * 본 테스트의 모든 결제는 PENDING 으로 안정적으로 관찰된다 (PAID/FAILED 확정·웹훅 처리는 #W7-4 범위 —
 * {@code PaymentWebhookIntegrationTest} 가 검증).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.mock.auto-webhook=false")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/payments 결제 요청·조회 API (#55)")
class PaymentApiIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private LabelRepository labelRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private JwtProvider jwtProvider;

    private Long ownerId;
    private Long otherMemberId;
    private Long albumId;
    private String ownerBearer;
    private String otherBearer;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: payment → orders → album → artist/genre/label, member
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        ownerId = memberRepository.saveAndFlush(
                Member.register("owner@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Owner", "01000000001")).getId();
        otherMemberId = memberRepository.saveAndFlush(
                Member.register("other@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Other", "01000000002")).getId();

        Long artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", null)).getId();
        Long genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        Long labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();
        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();
        Label label = labelRepository.findById(labelId).orElseThrow();
        albumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, 35000L, 100,
                        AlbumStatus.SELLING, false, null, null)).getId();

        ownerBearer = "Bearer " + jwtProvider.issueAccessToken(ownerId, MemberRole.USER);
        otherBearer = "Bearer " + jwtProvider.issueAccessToken(otherMemberId, MemberRole.USER);
    }

    private String nextOrderNumber() {
        return String.format("ORD-20260512-A%05d", ORDER_SEQ.incrementAndGet());
    }

    private Order persistOrder(Long memberId, OrderStatus status) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = (memberId != null)
                ? Order.placeForMember(nextOrderNumber(), memberId)
                : Order.placeForGuest(nextOrderNumber(), "guest@example.com", "01099999999");
        order.addItem(OrderItem.create(album, 1)); // totalAmount = 35000
        if (status != OrderStatus.PENDING) {
            order.changeStatus(status, null);
        }
        return orderRepository.saveAndFlush(order);
    }

    private String requestBody(String orderNumber, String method) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", orderNumber);
        body.put("method", method);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("회원 PENDING 주문 결제 요청 → 202 + paymentId, Payment PENDING 저장, 주문 상태 불변")
    void request_member_returns202_andSavesPendingPayment() throws Exception {
        Order order = persistOrder(ownerId, OrderStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-member-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order.getOrderNumber(), "CARD")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentId").value(notNullValue()))
                .andExpect(jsonPath("$.orderNumber").value(order.getOrderNumber()))
                .andExpect(jsonPath("$.amount").value(35000))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.pgProvider").value("MOCK"));

        assertThat(paymentRepository.count()).isEqualTo(1);
        Payment payment = paymentRepository.findAll().get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getOrder().getId()).isEqualTo(order.getId());
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("동일 Idempotency-Key 재요청 → 최초 응답 그대로 replay, Payment 1건")
    void request_sameIdempotencyKey_replaysFirstResponse() throws Exception {
        Order order = persistOrder(ownerId, OrderStatus.PENDING);
        String content = requestBody(order.getOrderNumber(), "CARD");

        MvcResult first = mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isAccepted())
                .andReturn();
        long firstPaymentId = objectMapper.readTree(first.getResponse().getContentAsString()).get("paymentId").asLong();

        MvcResult second = mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isAccepted())
                .andReturn();
        long secondPaymentId = objectMapper.readTree(second.getResponse().getContentAsString()).get("paymentId").asLong();

        assertThat(secondPaymentId).isEqualTo(firstPaymentId);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency-Key 헤더 누락 → 400 IDEMPOTENCY_KEY_REQUIRED")
    void request_missingIdempotencyKey_returns400() throws Exception {
        Order order = persistOrder(ownerId, OrderStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order.getOrderNumber(), "CARD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    @DisplayName("PENDING 아닌 주문 결제 요청 → 409 ORDER_INVALID_STATE_TRANSITION, Payment 미생성")
    void request_orderNotPending_returns409() throws Exception {
        Order order = persistOrder(ownerId, OrderStatus.PAID);

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-not-pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order.getOrderNumber(), "CARD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATE_TRANSITION"));

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    @DisplayName("게스트 주문 결제 요청 → 익명 호출자도 202")
    void request_guestOrder_anonymous_returns202() throws Exception {
        Order order = persistOrder(null, OrderStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments")
                        .header(IDEMPOTENCY_HEADER, "key-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order.getOrderNumber(), "MOCK")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()))
                .andExpect(jsonPath("$.method").value("MOCK"));

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("미존재 주문 결제 요청 → 404 ORDER_NOT_FOUND")
    void request_unknownOrder_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("ORD-20260512-Z99999", "CARD")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    @DisplayName("타 회원 주문 결제 요청 → 404 ORDER_NOT_FOUND, Payment 미생성")
    void request_memberOrder_byOtherMember_returns404() throws Exception {
        Order order = persistOrder(ownerId, OrderStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .header(IDEMPOTENCY_HEADER, "key-other-member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order.getOrderNumber(), "CARD")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 Idempotency-Key 를 다른 본문으로 재사용 → 409 IDEMPOTENCY_KEY_REUSE_MISMATCH")
    void request_keyReuseWithDifferentBody_returns409() throws Exception {
        Order first = persistOrder(ownerId, OrderStatus.PENDING);
        Order second = persistOrder(ownerId, OrderStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-reused")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(first.getOrderNumber(), "CARD")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer)
                        .header(IDEMPOTENCY_HEADER, "key-reused")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(second.getOrderNumber(), "CARD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSE_MISMATCH"));
    }

    @Test
    @DisplayName("결제 단건 조회 — 본인 → 200")
    void getPayment_owner_returns200() throws Exception {
        Long paymentId = createPaymentFor(persistOrder(ownerId, OrderStatus.PENDING), ownerBearer, "key-get-owner");

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.intValue()))
                .andExpect(jsonPath("$.status").value(PaymentStatus.PENDING.name()));
    }

    @Test
    @DisplayName("결제 단건 조회 — 타 회원 → 404 PAYMENT_NOT_FOUND")
    void getPayment_otherMember_returns404() throws Exception {
        Long paymentId = createPaymentFor(persistOrder(ownerId, OrderStatus.PENDING), ownerBearer, "key-get-other");

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("결제 단건 조회 — 미인증 → 401")
    void getPayment_unauthenticated_returns401() throws Exception {
        Long paymentId = createPaymentFor(persistOrder(ownerId, OrderStatus.PENDING), ownerBearer, "key-get-anon");

        mockMvc.perform(get("/api/v1/payments/{paymentId}", paymentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("결제 단건 조회 — 미존재 paymentId → 404 PAYMENT_NOT_FOUND")
    void getPayment_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{paymentId}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, ownerBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    private Long createPaymentFor(Order order, String bearer, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(order.getOrderNumber(), "CARD")))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("paymentId").asLong();
    }
}
