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
import com.groove.common.outbox.OutboxEventRepository;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
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
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토스 결제 웹훅 수신 통합 테스트.
 *
 * <p>Testcontainers MySQL 위 MockMvc, test 프로파일. test 에선 {@code MockPaymentGateway} 가 PaymentGateway 라
 * 결제 접수(POST /payments) 직후 query() 가 즉시 PAID 를 반환한다 → 재조회 검증 흐름이 PAID 로 합류한다.
 * 서명 헤더 없이 paymentKey 재조회만으로 신뢰하므로 본문 status 는 무시된다(위조 무력화).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.mock.auto-webhook=false")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/payments/toss/webhook 토스 결제 웹훅")
class TossWebhookIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final int INITIAL_STOCK = 100;
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private OutboxEventRepository outboxEventRepository;

    private Long memberId;
    private Long albumId;
    private String bearer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        memberId = memberRepository.saveAndFlush(
                MemberFixtures.register("buyer@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Buyer", "01000000001")).getId();
        Long artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", null)).getId();
        Long genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        Long labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();
        albumId = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artistRepository.findById(artistId).orElseThrow(),
                        genreRepository.findById(genreId).orElseThrow(), labelRepository.findById(labelId).orElseThrow(),
                        (short) 1969, AlbumFormat.LP_12, 35000L, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
        bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    private record Created(Long paymentId, Long orderId, String orderNumber, String pgTransactionId) {
    }

    /** PENDING 주문 생성 → POST /payments 로 결제 접수(PENDING 유지) → 식별자 묶음 반환. mock query(pgTx) 는 PAID 를 반환한다. */
    private Created createPendingPayment(int quantity) throws Exception {
        Album album = albumRepository.findById(albumId).orElseThrow();
        album.adjustStock(-quantity);
        albumRepository.saveAndFlush(album);

        String orderNumber = String.format("ORD-20260623-T%05d", ORDER_SEQ.incrementAndGet());
        Order order = Order.placeForMember(orderNumber, memberId, com.groove.support.OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, quantity));
        Long orderId = orderRepository.saveAndFlush(order).getId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", orderNumber);
        body.put("method", "CARD");
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(IDEMPOTENCY_HEADER, "key-" + orderNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());

        var payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        return new Created(payment.getId(), orderId, orderNumber, payment.getPgTransactionId());
    }

    private String tossWebhookBody(String eventType, String paymentKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventType", eventType);
        body.put("createdAt", "2026-06-23T12:00:00.000000"); // 토스 페이로드 잉여 필드 — ignoreUnknown 으로 무시됨을 함께 검증
        if (paymentKey != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("paymentKey", paymentKey);
            data.put("status", "DONE"); // 본문 status 는 신뢰하지 않음(재조회 우선) — 잉여 필드로 전송
            body.put("data", data);
        }
        return objectMapper.writeValueAsString(body);
    }

    private PaymentStatus paymentStatus(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow().getStatus();
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("PAYMENT_STATUS_CHANGED → paymentKey 재조회 PAID → 200 APPLIED, 결제·주문 PAID (서명 헤더 없이 공개 접근)")
    void webhook_paymentStatusChanged_confirmsViaRequery() throws Exception {
        Created c = createPendingPayment(1);
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments/toss/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tossWebhookBody(PAYMENT_STATUS_CHANGED, c.pgTransactionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(c.paymentId()).orElseThrow().getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("중복 웹훅 → 두 번째도 200, 상태 전이는 1회(공유 멱등키)")
    void webhook_duplicate_idempotent() throws Exception {
        Created c = createPendingPayment(1);
        String body = tossWebhookBody(PAYMENT_STATUS_CHANGED, c.pgTransactionId());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/payments/toss/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("알 수 없는 paymentKey → 로컬 선조회 미존재 → 재조회 없이 200 IGNORED (본문 status=DONE 무시)")
    void webhook_unknownPaymentKey_ignored() throws Exception {
        mockMvc.perform(post("/api/v1/payments/toss/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tossWebhookBody(PAYMENT_STATUS_CHANGED, "unknown-payment-key")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
    }

    @Test
    @DisplayName("대상 외 이벤트 → 200 IGNORED, 결제 불변")
    void webhook_otherEvent_ignored() throws Exception {
        Created c = createPendingPayment(1);

        mockMvc.perform(post("/api/v1/payments/toss/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tossWebhookBody("DEPOSIT_CALLBACK", c.pgTransactionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("data.paymentKey 누락 → 거부(400) 대신 200 IGNORED, 결제 불변")
    void webhook_missingPaymentKey_ignored() throws Exception {
        Created c = createPendingPayment(1);

        mockMvc.perform(post("/api/v1/payments/toss/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tossWebhookBody(PAYMENT_STATUS_CHANGED, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);
    }
}
