package com.groove.e2e;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.cart.domain.CartRepository;
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
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.shipping.application.ShippingProgressScheduler;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.review.domain.ReviewRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 핵심 구매 여정 E2E 통합 테스트 (#60) — 회원가입부터 리뷰까지 도메인 경계를 가로지르는 한 흐름을
 * 부팅된 애플리케이션 컨텍스트(실 필터·서비스·이벤트·스케줄러·Testcontainers MySQL) 위에서 검증한다.
 *
 * <p>도메인별 E2E 테스트({@code AuthFlowE2ETest}, {@code CartOrderE2EIntegrationTest},
 * {@code PaymentWebhookIntegrationTest}, {@code ShippingIntegrationTest}, {@code ReviewApiIntegrationTest})
 * 와 중복되지 않도록, 본 테스트는 단일 도메인 분기 케이스가 아니라 <strong>도메인 간 결합 지점(seam)</strong>과
 * G2 게이트 시나리오만 다룬다:
 * <ul>
 *   <li>회원 풀 여정 — 회원가입 → 로그인 → 장바구니 → 주문 → 결제 → PAID 웹훅 → (이벤트) 배송 생성
 *       → 자동 진행 DELIVERED → 리뷰 작성 → 앨범 평점 반영</li>
 *   <li>게스트 여정 — 게스트 주문 → 결제 → 웹훅 → 배송 생성 + {@code guest-lookup} 본인 조회,
 *       게스트 주문은 리뷰 불가(403)</li>
 *   <li>결제 실패 보상 트랜잭션 — FAILED 웹훅 → 주문 PAYMENT_FAILED + 재고 복원 + 배송 미생성</li>
 *   <li>멱등성 — 동일 {@code Idempotency-Key} 동시 결제 요청 → 단일 {@code Payment}</li>
 * </ul>
 *
 * <p><strong>주의 — 주문 상태 브리지:</strong> 배송 상태(PREPARING→SHIPPED→DELIVERED) 자동 진행은
 * {@code ShippingProgressScheduler} 가 {@code Shipping} 엔티티만 전이시키고, 이를 {@code Order} 상태로
 * 동기화하는 와이어링(또는 관리자 주문 상태 변경 API)은 아직 없다. 리뷰 작성 사전조건은
 * {@code order.status ∈ {DELIVERED, COMPLETED}} 이므로, 본 테스트는 배송을 실제 스케줄러로 끝까지 민 뒤
 * {@link #advanceOrderToDelivered(String)} 로 주문 상태를 리포지터리에서 직접 끌어올려 브리지한다
 * (도메인별 {@code ReviewApiIntegrationTest} 와 동일한 한계·동일한 처리). 주문↔배송 상태 동기화 와이어링은
 * 별도 이슈에서 다룬다.
 *
 * <p>결제 확정은 {@code payment.mock.auto-webhook=false} 로 인프로세스 자동 웹훅을 꺼 결정적으로 검증한다 —
 * {@code POST /api/v1/payments/webhook} 를 직접 호출하고({@code PaymentWebhookIntegrationTest} 와 동일 패턴),
 * 결제 완료 이벤트({@code OrderPaidEvent}, AFTER_COMMIT)로 비동기 생성되는 배송은 Awaitility 로 폴링 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "payment.mock.auto-webhook=false")
@Import(TestcontainersConfig.class)
@DisplayName("핵심 구매 여정 E2E — 회원/게스트/결제 실패 보상/멱등 (#60)")
class FullPurchaseJourneyE2ETest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String SIGNATURE_HEADER = "X-Mock-Signature";
    private static final String WEBHOOK_SECRET = "test-mock-webhook-secret"; // application-test.yaml
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$dummyhashvalueforintegrationtest...";
    private static final long ALBUM1_PRICE = 35_000L;
    private static final long ALBUM2_PRICE = 30_000L;
    private static final int INITIAL_STOCK = 100;

    /** AFTER_COMMIT 이벤트로 배송이 비동기 생성되는 데 허용하는 최대 대기 시간 (테스트 프로파일은 사실상 즉시). */
    private static final int SHIPPING_EVENT_TIMEOUT_SECONDS = 5;
    /** 멱등 동시성 검증에서 동일 키로 동시 발사할 요청 수. */
    private static final int CONCURRENT_PAYMENT_THREADS = 4;
    /** 동시 요청 워커가 모두 종료되기를 기다리는 최대 시간. */
    private static final int CONCURRENT_LATCH_TIMEOUT_SECONDS = 15;
    /** 서버 오류(5xx) 경계 — 멱등 동시 요청은 접수(202) 또는 처리 중 충돌(409) 중 하나여야 하고 5xx 는 없어야 한다. */
    private static final int SERVER_ERROR_THRESHOLD = 500;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private MemberRepository memberRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ShippingRepository shippingRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ShippingProgressScheduler shippingProgressScheduler;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private Long album1Id;
    private Long album2Id;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);

        cleanAll();

        Long artistId = artistRepository.saveAndFlush(Artist.create("The Beatles", "리버풀 출신 4인조")).getId();
        Long genreId = genreRepository.saveAndFlush(Genre.create("Rock")).getId();
        Long labelId = labelRepository.saveAndFlush(Label.create("Apple Records")).getId();
        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();
        Label label = labelRepository.findById(labelId).orElseThrow();

        album1Id = albumRepository.saveAndFlush(
                Album.create("Abbey Road", artist, genre, label,
                        (short) 1969, AlbumFormat.LP_12, ALBUM1_PRICE, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, "https://img/abbey-road", "1969 마스터")).getId();
        album2Id = albumRepository.saveAndFlush(
                Album.create("Let It Be", artist, genre, label,
                        (short) 1970, AlbumFormat.LP_12, ALBUM2_PRICE, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
    }

    /**
     * 본 테스트는 로그인 API 로 {@code refresh_token} 행을, PAID 흐름으로 {@code payment}/{@code shipping}/{@code review}
     * 행을 만든다. {@code refresh_token → member} FK 는 {@code ON DELETE CASCADE} 가 아니므로, 정리하지 않으면
     * 이후 테스트 클래스의 {@code member.deleteAllInBatch()} 가 깨진다 — {@code @AfterEach} 로도 한 번 더 비워
     * 다운스트림 오염을 막는다. ({@code payment}/{@code shipping}/{@code review} 는 {@code orders} 삭제 시 동반 삭제되지만,
     * {@code refresh_token} 은 그렇지 않다.)
     */
    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        // FK 의존 순서대로 부모 repository 를 비운다 — review_*, payment, shipping, cart_item, order_item 자식 행은
        // 부모 삭제와 함께 정리된다. refresh_token 은 member 에 비-CASCADE FK 를 가지므로 member 보다 먼저 비운다.
        refreshTokenRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        shippingRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("회원 풀 여정: 회원가입→로그인→장바구니→주문→결제→PAID 웹훅→배송 생성→자동 진행 DELIVERED→리뷰→평점 반영")
    void memberFullJourney() throws Exception {
        // 1) 회원가입 → 로그인
        signup("buyer@example.com", "P@ssw0rd!2024", "김민수", "01000000001");
        String bearer = "Bearer " + login("buyer@example.com", "P@ssw0rd!2024");

        // 2) 장바구니 — 추가는 재고를 차감하지 않는다 (W6 정책)
        addToCart(bearer, album1Id, 2);
        addToCart(bearer, album2Id, 1);
        assertThat(stockOf(album1Id)).isEqualTo(INITIAL_STOCK);

        // 3) 주문 생성 — 주문 시점에 재고 차감
        long expectedTotal = ALBUM1_PRICE * 2 + ALBUM2_PRICE;
        String orderNumber = placeMemberOrder(bearer, List.of(item(album1Id, 2), item(album2Id, 1)));
        assertThat(stockOf(album1Id)).isEqualTo(INITIAL_STOCK - 2);
        assertThat(orderStatusOf(orderNumber)).isEqualTo(OrderStatus.PENDING);

        // 4) 결제 접수 — auto-webhook 꺼져 있어 PENDING 유지
        String pgTransactionId = requestPayment(bearer, orderNumber, "idem-" + orderNumber);
        assertThat(paymentStatusForOrder(orderNumber)).isEqualTo(PaymentStatus.PENDING);

        // 5) PAID 웹훅 → 결제·주문 확정
        confirmPaymentPaid(pgTransactionId);
        assertThat(orderStatusOf(orderNumber)).isEqualTo(OrderStatus.PAID);
        assertThat(paymentStatusForOrder(orderNumber)).isEqualTo(PaymentStatus.PAID);

        // 6) OrderPaidEvent(AFTER_COMMIT) → 배송 자동 생성 (비동기) — 배송지 스냅샷·운송장 발급
        Shipping shipping = awaitShippingCreated();
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getRecipientName()).isEqualTo("김철수"); // 주문 생성 시 보낸 배송지 스냅샷
        String trackingNumber = shipping.getTrackingNumber();
        getShipping(trackingNumber)
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.status").value("PREPARING"));

        // 7) 자동 진행 스케줄러 — PREPARING → SHIPPED → DELIVERED (테스트 프로파일은 단계 지연 0)
        progressShippingToDelivered();
        getShipping(trackingNumber)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").exists());

        // 8) 주문 상태 브리지 (클래스 javadoc 참조) — 리뷰 사전조건(DELIVERED) 충족용
        advanceOrderToDelivered(orderNumber);

        // 9) 리뷰 작성 (실 API)
        postReview(bearer, orderNumber, album1Id, 5, "음질 정말 좋네요")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").isNumber())
                .andExpect(jsonPath("$.memberName").value("김**"))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.content").value("음질 정말 좋네요"));

        // 10) 앨범 상세에 평균 평점·리뷰 수 반영
        mockMvc.perform(get("/api/v1/albums/" + album1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));

        // 11) 회귀 신호 — 같은 (주문, 앨범) 두 번째 리뷰는 409, 평점은 그대로
        postReview(bearer, orderNumber, album1Id, 3, "또 작성?")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_DUPLICATED"));
        mockMvc.perform(get("/api/v1/albums/" + album1Id))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    @DisplayName("게스트 여정: 게스트 주문→결제→PAID 웹훅→배송 생성, guest-lookup 본인 조회, 게스트 주문은 리뷰 불가(403)")
    void guestJourney() throws Exception {
        String guestEmail = "guest@example.com";
        String orderNumber = placeGuestOrder(List.of(item(album1Id, 1)), guestEmail, "01099998888");

        // 게스트도 결제 시작 가능 (POST /payments permitAll)
        String pgTransactionId = requestPayment(null, orderNumber, "idem-" + orderNumber);
        confirmPaymentPaid(pgTransactionId);
        assertThat(orderStatusOf(orderNumber)).isEqualTo(OrderStatus.PAID);

        Shipping shipping = awaitShippingCreated();

        // guest-lookup — 맞는 email 은 200, 틀린 email 은 404 (존재 노출 회피)
        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/guest-lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", guestEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.totalAmount").value(ALBUM1_PRICE));
        mockMvc.perform(post("/api/v1/orders/" + orderNumber + "/guest-lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "wrong@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        // 배송을 DELIVERED 까지 민 뒤에도, 게스트 주문(memberId 부재)은 리뷰 작성 단계에서 403 으로 막힌다.
        progressShippingToDelivered();
        getShipping(shipping.getTrackingNumber()).andExpect(jsonPath("$.status").value("DELIVERED"));
        advanceOrderToDelivered(orderNumber);

        Long someMemberId = memberRepository.saveAndFlush(
                Member.register("member@example.com", DUMMY_PASSWORD_HASH, "박지성", "01000000009")).getId();
        String someMemberBearer = "Bearer " + jwtProvider.issueAccessToken(someMemberId, MemberRole.USER);
        postReview(someMemberBearer, orderNumber, album1Id, 4, "게스트 주문이라 안 됨")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_OWNED"));
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("결제 실패 보상: FAILED 웹훅 → 결제 FAILED·주문 PAYMENT_FAILED·재고 복원·배송 미생성")
    void paymentFailure_compensates() throws Exception {
        Long memberId = memberRepository.saveAndFlush(
                Member.register("buyer2@example.com", DUMMY_PASSWORD_HASH, "이영희", "01000000002")).getId();
        String bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);

        String orderNumber = placeMemberOrder(bearer, List.of(item(album1Id, 5)));
        assertThat(stockOf(album1Id)).isEqualTo(INITIAL_STOCK - 5);

        String pgTransactionId = requestPayment(bearer, orderNumber, "idem-" + orderNumber);
        failPayment(pgTransactionId, "카드 한도 초과");

        assertThat(paymentStatusForOrder(orderNumber)).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatusOf(orderNumber)).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(stockOf(album1Id)).as("실패 보상 — 차감했던 재고 복원").isEqualTo(INITIAL_STOCK);
        // 결제 실패 시 OrderPaidEvent 가 발행되지 않으므로 배송도 생성되지 않는다 (웹훅 처리는 동기 — 반환 시점에 확정).
        assertThat(shippingRepository.findAll()).as("실패 결제에는 배송이 생기지 않는다").isEmpty();
    }

    @Test
    @DisplayName("멱등성: 동일 Idempotency-Key 동시 결제 요청 → 단일 Payment, 응답은 202/409 만(5xx 없음)")
    void concurrentSameIdempotencyKey_singlePayment() throws Exception {
        Long memberId = memberRepository.saveAndFlush(
                Member.register("buyer3@example.com", DUMMY_PASSWORD_HASH, "최동석", "01000000003")).getId();
        String bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
        String orderNumber = placeMemberOrder(bearer, List.of(item(album1Id, 1)));
        String idempotencyKey = "idem-" + orderNumber;

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_PAYMENT_THREADS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_PAYMENT_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_PAYMENT_THREADS);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < CONCURRENT_PAYMENT_THREADS; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                                        .header(HttpHeaders.AUTHORIZATION, bearer)
                                        .header(IDEMPOTENCY_HEADER, idempotencyKey)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("orderNumber", orderNumber, "method", "CARD"))))
                                .andReturn();
                        statuses.add(result.getResponse().getStatus());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(SHIPPING_EVENT_TIMEOUT_SECONDS, SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(CONCURRENT_LATCH_TIMEOUT_SECONDS, SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).hasSize(CONCURRENT_PAYMENT_THREADS)
                .as("서버 오류(5xx) 없이 처리돼야 한다: %s", statuses)
                .allMatch(s -> s < SERVER_ERROR_THRESHOLD);
        assertThat(statuses).as("적어도 한 요청은 결제를 접수(202)해야 한다: %s", statuses).contains(202);
        assertThat(statuses).as("나머지 응답은 접수(202) 또는 처리 중 충돌(409) 중 하나여야 한다: %s", statuses)
                .allMatch(s -> s == 202 || s == 409);
        assertThat(paymentRepository.count()).as("동일 키 동시 요청은 단일 Payment 만 생성한다").isEqualTo(1);
    }

    // ---------- 요청 헬퍼 ----------

    private void signup(String email, String rawPassword, String name, String phone) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", rawPassword);
        body.put("name", name);
        body.put("phone", phone);
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", rawPassword))))
                .andExpect(status().isOk())
                .andReturn();
        return requireField(result, "accessToken");
    }

    private void addToCart(String bearer, Long albumId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(item(albumId, quantity))))
                .andExpect(status().isCreated());
    }

    private String placeMemberOrder(String bearer, List<Map<String, Object>> items) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("shipping", shippingBody());
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return requireField(result, "orderNumber");
    }

    private String placeGuestOrder(List<Map<String, Object>> items, String email, String phone) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("guest", Map.of("email", email, "phone", phone));
        body.put("shipping", shippingBody());
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return requireField(result, "orderNumber");
    }

    /** PENDING 주문에 대해 {@code POST /payments} 로 결제를 접수하고(202) PG 거래 식별자를 돌려준다. */
    private String requestPayment(String bearerOrNull, String orderNumber, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/payments")
                .header(IDEMPOTENCY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderNumber", orderNumber, "method", "CARD")));
        if (bearerOrNull != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, bearerOrNull);
        }
        mockMvc.perform(request).andExpect(status().isAccepted());
        Long orderId = orderRepository.findByOrderNumber(orderNumber).orElseThrow().getId();
        return paymentRepository.findByOrderId(orderId).orElseThrow().getPgTransactionId();
    }

    /** PG PAID 웹훅을 직접 호출해 결제·주문을 확정한다. */
    private void confirmPaymentPaid(String pgTransactionId) throws Exception {
        postWebhook(pgTransactionId, PaymentStatus.PAID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    /** PG FAILED 웹훅을 직접 호출해 보상 트랜잭션(재고 복원 + 주문 PAYMENT_FAILED)을 트리거한다. */
    private void failPayment(String pgTransactionId, String failureReason) throws Exception {
        postWebhook(pgTransactionId, PaymentStatus.FAILED, failureReason)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"));
    }

    private ResultActions postWebhook(String pgTransactionId, PaymentStatus result, String failureReason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pgTransactionId", pgTransactionId);
        body.put("status", result.name());
        if (failureReason != null) {
            body.put("failureReason", failureReason);
        }
        return mockMvc.perform(post("/api/v1/payments/webhook")
                .header(SIGNATURE_HEADER, WEBHOOK_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions postReview(String bearer, String orderNumber, Long albumId, int rating, String content) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", orderNumber);
        body.put("albumId", albumId);
        body.put("rating", rating);
        body.put("content", content);
        return mockMvc.perform(post("/api/v1/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions getShipping(String trackingNumber) throws Exception {
        return mockMvc.perform(get("/api/v1/shippings/" + trackingNumber)).andExpect(status().isOk());
    }

    // ---------- 비동기/스케줄러/상태 헬퍼 ----------

    /** {@code OrderPaidEvent}(AFTER_COMMIT)로 비동기 생성되는 배송 1건이 나타날 때까지 폴링하고 그 엔티티를 돌려준다. */
    private Shipping awaitShippingCreated() {
        await().atMost(SHIPPING_EVENT_TIMEOUT_SECONDS, SECONDS)
                .untilAsserted(() -> assertThat(shippingRepository.findAll()).hasSize(1));
        return shippingRepository.findAll().get(0);
    }

    /** 자동 진행 스케줄러를 두 번 돌려 단일 배송을 PREPARING → SHIPPED → DELIVERED 로 민다 (테스트 프로파일은 단계 지연 0). */
    private void progressShippingToDelivered() {
        shippingProgressScheduler.progressShipments();
        assertThat(singleShipping().getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        shippingProgressScheduler.progressShipments();
        assertThat(singleShipping().getStatus()).isEqualTo(ShippingStatus.DELIVERED);
    }

    private Shipping singleShipping() {
        List<Shipping> all = shippingRepository.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    /**
     * 배송 자동 진행이 {@code Order} 상태로 동기화되는 경로가 아직 없어, 리뷰 사전조건(DELIVERED)을 위해
     * 주문 상태를 PAID → PREPARING → SHIPPED → DELIVERED 로 직접 끌어올린다 (클래스 javadoc 참조).
     */
    private void advanceOrderToDelivered(String orderNumber) {
        tx.executeWithoutResult(status -> {
            Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
            order.changeStatus(OrderStatus.PREPARING, null);
            order.changeStatus(OrderStatus.SHIPPED, null);
            order.changeStatus(OrderStatus.DELIVERED, null);
        });
    }

    private OrderStatus orderStatusOf(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus();
    }

    private PaymentStatus paymentStatusForOrder(String orderNumber) {
        Long orderId = orderRepository.findByOrderNumber(orderNumber).orElseThrow().getId();
        return paymentRepository.findByOrderId(orderId).orElseThrow().getStatus();
    }

    private int stockOf(Long albumId) {
        return albumRepository.findById(albumId).orElseThrow().getStock();
    }

    // ---------- 본문/직렬화 헬퍼 ----------

    private static Map<String, Object> item(Long albumId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("albumId", albumId);
        body.put("quantity", quantity);
        return body;
    }

    private static Map<String, Object> shippingBody() {
        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("recipientName", "김철수");
        shipping.put("recipientPhone", "01012345678");
        shipping.put("address", "서울시 강남구 테헤란로 123");
        shipping.put("addressDetail", "456호");
        shipping.put("zipCode", "06234");
        shipping.put("safePackagingRequested", false);
        return shipping;
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String requireField(MvcResult result, String field) throws Exception {
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        return Objects.requireNonNull(json.get(field), () -> "응답 JSON 에 '" + field + "' 필드가 없음: " + json).asText();
    }
}
