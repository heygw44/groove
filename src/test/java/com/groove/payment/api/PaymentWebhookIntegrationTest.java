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
import com.groove.payment.application.PaymentReconciliationScheduler;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.InvalidWebhookSignatureException;
import com.groove.payment.gateway.WebhookDispatcher;
import com.groove.payment.gateway.WebhookNotification;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 웹훅 수신 + 보상 트랜잭션 + 폴링 동기화 통합 테스트 (#W7-4).
 *
 * <p>Testcontainers MySQL 위 MockMvc. {@code payment.mock.auto-webhook=false} 로 인프로세스 자동 웹훅을 꺼
 * 결정적으로 검증한다 — 웹훅은 {@code POST /api/v1/payments/webhook} 를 직접 호출하고, 폴링은
 * {@link PaymentReconciliationScheduler#reconcilePendingPayments()} 를 직접 호출한다. {@code test} 프로파일은
 * {@code success-rate=1.0}, {@code webhook-delay=0}, {@code reconciliation.min-age=PT0S} 이라
 * {@code MockPaymentGateway.query()} 가 (request() 가 거래를 등록한 직후라면) 즉시 PAID 를 반환한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.mock.auto-webhook=false")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/payments/webhook 결제 웹훅 + 보상 + 폴링 (#W7-4)")
class PaymentWebhookIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String SIGNATURE_HEADER = "X-Mock-Signature";
    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret"; // application-test.yaml
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
    @Autowired private PaymentReconciliationScheduler reconciliationScheduler;
    @Autowired private WebhookDispatcher webhookDispatcher; // 인프로세스 경로 = PaymentWebhookHandler (LoggingWebhookDispatcher 대체)

    private Long memberId;
    private Long albumId;
    private String bearer;

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

        memberId = memberRepository.saveAndFlush(
                Member.register("buyer@example.com", "$2a$10$dummyhashvalueforintegrationtest...", "Buyer", "01000000001")).getId();
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

    /** PENDING 주문 생성 → POST /payments 로 결제 접수 (auto-webhook 꺼져 있어 PENDING 유지) → 식별자 묶음 반환. */
    private Created createPendingPayment(int quantity) throws Exception {
        Album album = albumRepository.findById(albumId).orElseThrow();
        album.adjustStock(-quantity);            // OrderService.place 의 재고 차감을 모사한다 (테스트는 Order 를 직접 영속화).
        albumRepository.saveAndFlush(album);

        String orderNumber = String.format("ORD-20260512-W%05d", ORDER_SEQ.incrementAndGet());
        Order order = Order.placeForMember(orderNumber, memberId);
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

    private String webhookBody(String pgTransactionId, PaymentStatus result, String failureReason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pgTransactionId", pgTransactionId);
        body.put("status", result.name());
        if (failureReason != null) {
            body.put("failureReason", failureReason);
        }
        return objectMapper.writeValueAsString(body);
    }

    private PaymentStatus paymentStatus(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow().getStatus();
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private int currentStock() {
        return albumRepository.findById(albumId).orElseThrow().getStock();
    }

    @Test
    @DisplayName("정상 PAID 웹훅 → 200, 결제 PAID·주문 PAID·paidAt 기록")
    void webhook_paid_confirmsPaymentAndOrder() throws Exception {
        Created c = createPendingPayment(1);
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header(SIGNATURE_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody(c.pgTransactionId(), PaymentStatus.PAID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(c.paymentId()).orElseThrow().getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("실패 웹훅 → 200, 결제 FAILED·주문 PAYMENT_FAILED·재고 복원")
    void webhook_failed_compensates() throws Exception {
        Created c = createPendingPayment(3); // 재고 100 → 97
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK - 3);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header(SIGNATURE_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody(c.pgTransactionId(), PaymentStatus.FAILED, "카드 한도 초과")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"));

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK);
        assertThat(paymentRepository.findById(c.paymentId()).orElseThrow().getFailureReason()).isEqualTo("카드 한도 초과");
    }

    @Test
    @DisplayName("중복 FAILED 웹훅 → 두 번째도 200, 상태 전이·재고 복원은 1회")
    void webhook_duplicateFailed_idempotent() throws Exception {
        Created c = createPendingPayment(2); // 100 → 98
        String body = webhookBody(c.pgTransactionId(), PaymentStatus.FAILED, "한도 초과");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header(SIGNATURE_HEADER, WEBHOOK_SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK); // 98 + 2, 한 번만 복원
    }

    @Test
    @DisplayName("잘못된 서명 → 401, 결제·주문 불변")
    void webhook_invalidSignature_returns401() throws Exception {
        Created c = createPendingPayment(1);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header(SIGNATURE_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody(c.pgTransactionId(), PaymentStatus.PAID, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAYMENT_WEBHOOK_INVALID_SIGNATURE"));

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("서명 헤더 누락 → 401")
    void webhook_missingSignature_returns401() throws Exception {
        Created c = createPendingPayment(1);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody(c.pgTransactionId(), PaymentStatus.PAID, null)))
                .andExpect(status().isUnauthorized());

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("알 수 없는 거래 식별자 → 200 IGNORED (무해)")
    void webhook_unknownTransaction_ignored() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header(SIGNATURE_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("mock-tx-does-not-exist", PaymentStatus.PAID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
    }

    @Test
    @DisplayName("status 가 PAID/FAILED 가 아니면 400")
    void webhook_invalidStatus_returns400() throws Exception {
        Created c = createPendingPayment(1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pgTransactionId", c.pgTransactionId());
        body.put("status", "PENDING");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header(SIGNATURE_HEADER, WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("폴링 스케줄러가 웹훅 미수신 PENDING 결제를 동기화한다")
    void reconciliation_syncsPendingPayment() throws Exception {
        Created c = createPendingPayment(1);
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);

        reconciliationScheduler.reconcilePendingPayments();

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID); // mock query() → PAID (success-rate=1.0)
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("인프로세스 웹훅 디스패처(PaymentWebhookHandler) → 서명 검증 후 결제·주문 확정")
    void inProcessDispatcher_paid_confirmsPaymentAndOrder() throws Exception {
        Created c = createPendingPayment(1);
        WebhookNotification notification = new WebhookNotification(
                c.pgTransactionId(), c.orderNumber(), PaymentStatus.PAID, Instant.now(), WEBHOOK_SECRET);

        webhookDispatcher.dispatch(notification);

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("인프로세스 웹훅 디스패처 — 잘못된 서명이면 401 예외, 결제 불변")
    void inProcessDispatcher_invalidSignature_throwsAndLeavesPending() throws Exception {
        Created c = createPendingPayment(1);
        WebhookNotification badSig = new WebhookNotification(
                c.pgTransactionId(), c.orderNumber(), PaymentStatus.PAID, Instant.now(), "wrong-secret");

        assertThatThrownBy(() -> webhookDispatcher.dispatch(badSig))
                .isInstanceOf(InvalidWebhookSignatureException.class);

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PENDING);
    }
}
