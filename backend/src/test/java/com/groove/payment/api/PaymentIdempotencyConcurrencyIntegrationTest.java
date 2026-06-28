package com.groove.payment.api;

import com.groove.auth.domain.RefreshTokenRepository;
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
import com.groove.common.idempotency.IdempotencyRecord;
import com.groove.common.idempotency.IdempotencyRecordRepository;
import com.groove.common.idempotency.IdempotencyStatus;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.application.PaymentCallbackService;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.support.ConcurrencyHarness;
import com.groove.support.MemberFixtures;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 멱등성 실 HTTP 동시성 통합. 동시 동일 Idempotency-Key 요청과 중복 웹훅이 각각 단일 결제 생성·전이 1회로
 * 수렴함을 검증한다. 셋업 save 는 비-@Transactional 로 커밋해야 RANDOM_PORT 서버 스레드에서 보인다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "payment.mock.auto-webhook=false",
        // Hikari 풀·rate limit 을 동시성 한도보다 크게.
        "spring.datasource.hikari.maximum-pool-size=32",
        "groove.payment.rate-limit.post.capacity=100"
})
@Import(TestcontainersConfig.class)
@DisplayName("결제 멱등성 동시성 통합 테스트")
class PaymentIdempotencyConcurrencyIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String SIGNATURE_HEADER = "X-Mock-Signature";
    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret";
    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_REQUESTS = 16;
    private static final int THREAD_POOL_SIZE = 16;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort private int port;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private com.groove.common.outbox.OutboxEventRepository outboxEventRepository; // PAID 웹훅이 커밋하는 ORDER_PAID 행

    private int orderSeq;
    private Long memberId;
    private Long albumId;
    private String bearer;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        orderSeq = 0;
        // FK 의존 순서: payment → orders → album → artist/genre/label, member (refresh_token 도 member 전에).
        idempotencyRecordRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
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

    @Test
    @DisplayName("동시 동일 Idempotency-Key 결제 요청 16건 → Payment 정확히 1건, 응답은 202/409 (5xx 0)")
    void concurrentSameIdempotencyKey_createsSinglePayment() throws Exception {
        Order order = persistPendingOrder();
        String key = "concurrent-pay-" + order.getOrderNumber();
        String body = paymentRequestBody(order.getOrderNumber(), "CARD");

        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
        ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, CONCURRENT_REQUESTS, i ->
                statuses.add(post("/api/v1/payments", body, builder -> {
                    builder.header("Authorization", bearer);
                    builder.header(IDEMPOTENCY_HEADER, key);
                })));

        assertThat(paymentRepository.count()).isEqualTo(1);

        long accepted = statuses.stream().filter(s -> s == 202).count();   // 생성 또는 캐시 replay
        long conflict = statuses.stream().filter(s -> s == 409).count();   // IDEMPOTENCY_IN_PROGRESS
        assertThat(statuses).hasSize(CONCURRENT_REQUESTS);
        assertThat(accepted).isGreaterThanOrEqualTo(1);
        assertThat(accepted + conflict).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(statuses.stream().noneMatch(s -> s >= 500)).isTrue();

        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    @DisplayName("동시 중복 FAILED 웹훅 16건 → 결제 FAILED·재고 복원 1회·주문 PAYMENT_FAILED (5xx 0)")
    void concurrentDuplicateFailedWebhook_transitionsOnce() throws Exception {
        Created c = createPendingPayment(2); // 재고 100 → 98
        String body = webhookBody(c.pgTransactionId(), PaymentStatus.FAILED, "한도 초과");

        ConcurrentLinkedQueue<Integer> statuses = fireConcurrentWebhooks(body);

        // 전이·보상 1회: 재고가 INITIAL 로만 복원.
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK);

        assertSingleTransitionResponses(statuses, c.pgTransactionId());
    }

    @Test
    @DisplayName("동시 중복 PAID 웹훅 16건 → 결제 PAID 1회·주문 PAID (5xx 0)")
    void concurrentDuplicatePaidWebhook_transitionsOnce() throws Exception {
        Created c = createPendingPayment(1);
        String body = webhookBody(c.pgTransactionId(), PaymentStatus.PAID, null);

        ConcurrentLinkedQueue<Integer> statuses = fireConcurrentWebhooks(body);

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findById(c.paymentId()).orElseThrow().getPaidAt()).isNotNull();

        assertSingleTransitionResponses(statuses, c.pgTransactionId());
    }

    private ConcurrentLinkedQueue<Integer> fireConcurrentWebhooks(String body) throws InterruptedException {
        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
        ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, CONCURRENT_REQUESTS, i ->
                statuses.add(post("/api/v1/payments/webhook", body,
                        builder -> builder.header(SIGNATURE_HEADER, WEBHOOK_SECRET))));
        return statuses;
    }

    /** 동시 중복 웹훅 공통 단언: 응답은 200·409 뿐이며 멱등 마커는 단일 COMPLETED. */
    private void assertSingleTransitionResponses(ConcurrentLinkedQueue<Integer> statuses, String pgTransactionId) {
        long ok = statuses.stream().filter(s -> s == 200).count();
        long conflict = statuses.stream().filter(s -> s == 409).count();
        assertThat(statuses).hasSize(CONCURRENT_REQUESTS);
        assertThat(ok).isGreaterThanOrEqualTo(1);
        assertThat(ok + conflict).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(statuses.stream().noneMatch(s -> s >= 500)).isTrue();

        IdempotencyRecord record = idempotencyRecordRepository
                .findByIdempotencyKey(PaymentCallbackService.idempotencyKeyFor(pgTransactionId)).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    /** path 로 JSON POST 하고 응답 status code 를 반환한다. */
    private int post(String path, String body, Consumer<HttpRequest.Builder> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.accept(builder);
        try {
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private record Created(Long paymentId, Long orderId, String orderNumber, String pgTransactionId) {
    }

    private String nextOrderNumber() {
        return String.format("ORD-20260512-C%05d", ++orderSeq);
    }

    private Order persistPendingOrder() {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = Order.placeForMember(nextOrderNumber(), memberId, OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, 1));
        return orderRepository.saveAndFlush(order);
    }

    /** PENDING 주문 + HTTP 결제 접수(PENDING 유지). 재고는 직접 차감. */
    private Created createPendingPayment(int quantity) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        album.adjustStock(-quantity);
        albumRepository.saveAndFlush(album);

        String orderNumber = nextOrderNumber();
        Order order = Order.placeForMember(orderNumber, memberId, OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, quantity));
        Long orderId = orderRepository.saveAndFlush(order).getId();

        int status = post("/api/v1/payments", paymentRequestBody(orderNumber, "CARD"), builder -> {
            builder.header("Authorization", bearer);
            builder.header(IDEMPOTENCY_HEADER, "key-" + orderNumber);
        });
        assertThat(status).isEqualTo(202);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        return new Created(payment.getId(), orderId, orderNumber, payment.getPgTransactionId());
    }

    private String paymentRequestBody(String orderNumber, String method) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", orderNumber);
        body.put("method", method);
        return objectMapper.writeValueAsString(body);
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
}
