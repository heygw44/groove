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
 * 결제 멱등성 — 실 HTTP 동시성 통합 테스트 (#207 / W11-1).
 *
 * <p>baseline(#196 {@code baseline.md} §1)이 k6 payment 시나리오에서 박제한 불변식 {@code payment_duplicated=0}
 * 을 회귀로 고정한다. 동시 동일 {@code Idempotency-Key} 결제 요청과 동시 중복 웹훅이 각각 <b>단일 결제 생성</b>·
 * <b>상태 전이 1회</b>로 수렴함을 검증한다.
 *
 * <p>MockMvc 는 running server 가 없어 멀티스레드 {@code perform()} 에 thread-safe 하지 않으므로(Spring
 * Framework reference), {@code @SpringBootTest(RANDOM_PORT)} 로 임베디드 서버를 띄우고 JDK {@link HttpClient}
 * 로 <b>진짜 동시 HTTP</b> 를 발사한다 — 인터셉터({@code @Idempotent})·컨트롤러·{@code IdempotencyService}·서비스·DB
 * 전 스택을 거친다. (이 앱은 클라이언트측 {@code spring-boot-restclient} 를 두지 않으므로 {@code TestRestTemplate}
 * 대신 의존성 없는 JDK {@code HttpClient} 를 쓴다 — {@code HttpClient} 는 thread-safe 해 단일 인스턴스를 공유한다.)
 * 동시 출발/완료 집계는 공용 {@link ConcurrencyHarness}(#205) 를 재사용한다.
 *
 * <p>이 클래스는 {@code @Transactional} 이 아니므로 셋업 repo save 가 커밋돼 서버 요청 스레드에서 보인다.
 * {@code payment.mock.auto-webhook=false} 로 인프로세스 자동 웹훅을 꺼 결제를 PENDING 으로 안정 관찰하고,
 * 웹훅은 {@code POST /api/v1/payments/webhook} 를 직접 동시 호출한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "payment.mock.auto-webhook=false",
        // 16 동시 요청(+PAID 경로의 콜백 REQUIRED·배송 REQUIRES_NEW 중첩)이 Hikari 기본 풀(10)을 넘겨
        // connection-timeout 5xx 로 플레이키해지지 않도록 풀을 동시성 한도의 2배로 명시한다.
        "spring.datasource.hikari.maximum-pool-size=32"
})
@Import(TestcontainersConfig.class)
@DisplayName("결제 멱등성 동시성 통합 테스트 (#207)")
class PaymentIdempotencyConcurrencyIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String SIGNATURE_HEADER = "X-Mock-Signature";
    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret"; // application-test.yaml payment.mock.webhook-secret
    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_REQUESTS = 16;
    private static final int THREAD_POOL_SIZE = 16;

    private static final HttpClient HTTP = HttpClient.newHttpClient(); // thread-safe — 동시 요청에 공유

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

    private int orderSeq;
    private Long memberId;
    private Long albumId;
    private String bearer;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        orderSeq = 0;
        // FK 의존 순서: payment → orders → album → artist/genre/label, member. (refresh_token → member 도 먼저)
        idempotencyRecordRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
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

    // ---- 시나리오 1: 동시 동일 Idempotency-Key 결제 요청 → 단일 결제 생성 ----

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

        // 핵심 불변식: 동시 요청이 몰려도 결제는 정확히 1건만 생성된다 (payment_duplicated=0).
        assertThat(paymentRepository.count()).isEqualTo(1);

        long accepted = statuses.stream().filter(s -> s == 202).count();   // 생성 또는 캐시 replay
        long conflict = statuses.stream().filter(s -> s == 409).count();   // IDEMPOTENCY_IN_PROGRESS
        assertThat(statuses).hasSize(CONCURRENT_REQUESTS);
        assertThat(accepted).isGreaterThanOrEqualTo(1);                    // 최소 한 요청은 처리/replay 성공
        assertThat(accepted + conflict).isEqualTo(CONCURRENT_REQUESTS);    // 그 외(특히 5xx)는 없다
        assertThat(statuses.stream().noneMatch(s -> s >= 500)).isTrue();

        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    // ---- 시나리오 2: 동시 중복 웹훅 → 상태 전이 1회 ----

    @Test
    @DisplayName("동시 중복 FAILED 웹훅 16건 → 결제 FAILED·재고 복원 1회·주문 PAYMENT_FAILED (5xx 0)")
    void concurrentDuplicateFailedWebhook_transitionsOnce() throws Exception {
        Created c = createPendingPayment(2); // 재고 100 → 98
        String body = webhookBody(c.pgTransactionId(), PaymentStatus.FAILED, "한도 초과");

        ConcurrentLinkedQueue<Integer> statuses = fireConcurrentWebhooks(body);

        // 상태 전이·보상은 정확히 1회: 재고가 N배 복원되지 않고 INITIAL 로만 돌아온다.
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK);

        assertSingleTransitionResponses(statuses, c.pgTransactionId());
    }

    @Test
    @DisplayName("동시 중복 PAID 웹훅 16건 → 결제 PAID 1회·주문 PREPARING(배송 락스텝) (5xx 0)")
    void concurrentDuplicatePaidWebhook_transitionsOnce() throws Exception {
        Created c = createPendingPayment(1);
        String body = webhookBody(c.pgTransactionId(), PaymentStatus.PAID, null);

        ConcurrentLinkedQueue<Integer> statuses = fireConcurrentWebhooks(body);

        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        // 결제 확정 + AFTER_COMMIT 배송 생성으로 주문은 PAID 거쳐 PREPARING 까지 락스텝 전진 (이슈 #161)
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PREPARING);
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

    /** 동시 중복 웹훅 공통 단언: 응답은 200(APPLIED/replay)·409(IN_PROGRESS) 뿐이며 멱등 마커는 단일 COMPLETED. */
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

    // ---- HTTP ----

    /** {@code path} 로 JSON POST. 응답 status code 반환 (4xx/5xx 도 예외 없이 그대로 반환). */
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

    // ---- 도메인 헬퍼 ----

    private record Created(Long paymentId, Long orderId, String orderNumber, String pgTransactionId) {
    }

    private String nextOrderNumber() {
        return String.format("ORD-20260512-C%05d", ++orderSeq);
    }

    private Order persistPendingOrder() {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = Order.placeForMember(nextOrderNumber(), memberId, OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, 1)); // totalAmount = 35000
        return orderRepository.saveAndFlush(order);
    }

    /** PENDING 주문 + HTTP 결제 접수(auto-webhook 꺼져 PENDING 유지). 재고는 OrderService.place 를 모사해 직접 차감. */
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
