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
import com.groove.common.idempotency.IdempotencyRecordRepository;
import com.groove.common.outbox.OutboxEventRepository;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.event.OrderPaidEvent;
import com.groove.payment.application.PaymentReconciliationScheduler;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.support.MemberFixtures;
import com.groove.support.OrderFixtures;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토스 confirm 승인 흐름 통합 테스트 — checkout → successUrl/failUrl 콜백.
 *
 * <p>Testcontainers MySQL 위 MockMvc, test 프로파일이라 PaymentGateway 는 {@code MockPaymentGateway}(confirm 항상 PAID).
 * checkout 은 게이트웨이 호출 없이 PENDING(잠정 pgTx=orderNumber)을 만들고, success 콜백이 confirm→PAID 적용,
 * fail 콜백이 보상(재고·쿠폰 복원)을 적용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("토스 confirm 흐름: /payments/toss/checkout·success·fail")
class TossPaymentConfirmIntegrationTest {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int INITIAL_STOCK = 100;
    private static final long UNIT_PRICE = 35_000L;
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private MemberCouponRepository memberCouponRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private PaymentReconciliationScheduler reconciliationScheduler;

    private Long memberId;
    private Long albumId;
    private String bearer;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        idempotencyRecordRepository.deleteAllInBatch(); // confirm/fail 멱등 키가 테스트 간 누수되지 않도록 정리
        outboxEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        memberCouponRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
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
                        (short) 1969, AlbumFormat.LP_12, UNIT_PRICE, INITIAL_STOCK,
                        AlbumStatus.SELLING, false, null, null)).getId();
        bearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    private record Checked(Long orderId, String orderNumber, long payable, String paymentKey, String callbackToken) {
    }

    /** PENDING 주문 생성(재고 차감) → 토스 checkout 으로 PENDING 결제(잠정 pgTx=orderNumber) 접수. */
    private Checked checkout(int quantity) throws Exception {
        Album album = albumRepository.findById(albumId).orElseThrow();
        album.adjustStock(-quantity);
        albumRepository.saveAndFlush(album);

        String orderNumber = String.format("ORD-20260622-T%05d", ORDER_SEQ.incrementAndGet());
        Order order = Order.placeForMember(orderNumber, memberId, OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, quantity));
        Long orderId = orderRepository.saveAndFlush(order).getId();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNumber", orderNumber);
        body.put("method", "CARD");
        mockMvc.perform(post("/api/v1/payments/toss/checkout")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(IDEMPOTENCY_HEADER, "ck-" + orderNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderNumber))
                .andExpect(jsonPath("$.amount").value(UNIT_PRICE * quantity));

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        // paymentKey 는 결제마다 고유 — 토스가 발급하는 paymentKey 가 고유하듯 멱등 키 충돌을 피한다.
        // callbackToken: checkout 이 발급·저장한 결제별 토큰 — successUrl 콜백이 이 값을 round-trip 해야 confirm 이 통과한다.
        return new Checked(orderId, orderNumber, payment.getAmount(), "pk-" + orderNumber, payment.getCallbackToken());
    }

    private PaymentStatus paymentStatus(Long orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow().getStatus();
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private int currentStock() {
        return albumRepository.findById(albumId).orElseThrow().getStock();
    }

    private long orderPaidEventCount() {
        return outboxEventRepository.findAll().stream()
                .filter(e -> OrderPaidEvent.OUTBOX_EVENT_TYPE.equals(e.getEventType()))
                .count();
    }

    @Test
    @DisplayName("checkout: 잠정 pgTx=orderNumber·provider=TOSS 로 PENDING 결제 저장, orderId·amount 응답")
    void checkout_createsPendingPayment() throws Exception {
        Checked c = checkout(1);

        Payment payment = paymentRepository.findByOrderId(c.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPgTransactionId()).isEqualTo("toss-pending:" + c.orderNumber()); // 잠정 pgTx(접두사)
        assertThat(payment.getPgProvider()).isEqualTo("TOSS");
        assertThat(payment.getAmount()).isEqualTo(UNIT_PRICE);
    }

    @Test
    @DisplayName("successUrl: confirm 성공 → 302 success, 결제·주문 PAID, paymentKey 연결, ORDER_PAID 이벤트 발행")
    void success_confirmsPaidAndPublishesEvent() throws Exception {
        Checked c = checkout(1);

        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", c.paymentKey())
                        .param("orderId", c.orderNumber())
                        .param("amount", String.valueOf(c.payable()))
                        .param("token", c.callbackToken()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=success"));

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
        Payment payment = paymentRepository.findByOrderId(c.orderId()).orElseThrow();
        assertThat(payment.getPgTransactionId()).isEqualTo(c.paymentKey()); // 잠정값 → 실제 paymentKey 교체
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(orderPaidEventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("successUrl: 금액 위변조(amount 불일치) → 302 fail, 결제·주문 PENDING 유지(미승인)")
    void success_amountMismatch_rejected() throws Exception {
        Checked c = checkout(1);

        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", c.paymentKey())
                        .param("orderId", c.orderNumber())
                        .param("amount", String.valueOf(c.payable() + 1_000)) // 조작된 금액
                        .param("token", c.callbackToken())) // 토큰은 유효 → 금액 검증 단계까지 도달
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=fail"));

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PENDING);
        assertThat(orderPaidEventCount()).isZero();
    }

    @Test
    @DisplayName("successUrl: 토큰 불일치 → 302 fail, 결제·주문 PENDING 유지(교차 주문 조작 차단)")
    void success_tokenMismatch_rejected() throws Exception {
        Checked c = checkout(1);

        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", c.paymentKey())
                        .param("orderId", c.orderNumber())
                        .param("amount", String.valueOf(c.payable()))
                        .param("token", "forged-token")) // 위조 토큰
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=fail"));

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PENDING);
        assertThat(orderPaidEventCount()).isZero();
    }

    @Test
    @DisplayName("successUrl: 토큰 누락 → 302 fail, 결제 PENDING 유지")
    void success_tokenMissing_rejected() throws Exception {
        Checked c = checkout(1);

        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", c.paymentKey())
                        .param("orderId", c.orderNumber())
                        .param("amount", String.valueOf(c.payable()))) // token 없음
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=fail"));

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderPaidEventCount()).isZero();
    }

    @Test
    @DisplayName("successUrl: 경계 초과(과대 token) → JSON 누출 없이 302 fail, 결제 PENDING 유지(컨트롤러 입력 가드)")
    void success_oversizedParam_redirectsFail() throws Exception {
        Checked c = checkout(1);
        String hugeToken = "x".repeat(200); // MAX_CALLBACK_PARAM_LENGTH(64) 초과

        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", c.paymentKey())
                        .param("orderId", c.orderNumber())
                        .param("amount", String.valueOf(c.payable()))
                        .param("token", hugeToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=fail"));

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderPaidEventCount()).isZero();
    }

    @Test
    @DisplayName("successUrl 재호출(새로고침): 멱등 — 주문 PAID 유지, ORDER_PAID 이벤트 1회")
    void success_calledTwice_idempotent() throws Exception {
        Checked c = checkout(1);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/payments/toss/success")
                            .param("paymentKey", c.paymentKey())
                            .param("orderId", c.orderNumber())
                            .param("amount", String.valueOf(c.payable()))
                            .param("token", c.callbackToken()))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=success"));
        }

        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
        assertThat(orderPaidEventCount()).isEqualTo(1); // 한 번만 발행
    }

    @Test
    @DisplayName("failUrl: 미인증 GET 이라 상태를 바꾸지 않고 302 fail 안내만 — 결제·주문·재고 PENDING 불변(교차 조작 차단)")
    void fail_redirectsOnly_noStateChange() throws Exception {
        Checked c = checkout(2); // 재고 100 → 98
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK - 2);

        mockMvc.perform(get("/payments/toss/fail")
                        .param("code", "PAY_PROCESS_CANCELED")
                        .param("message", "사용자가 결제를 취소했습니다")
                        .param("orderId", c.orderNumber()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=fail"));

        // failUrl 은 보상하지 않는다 — 보상은 만료 리퍼의 신뢰 경로가 담당.
        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PENDING);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PENDING);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK - 2); // 재고 미복원
    }

    @Test
    @DisplayName("만료 리퍼: 미확정 토스 PENDING 을 FAILED 로 정리하고 재고·쿠폰을 복원한다(신뢰 가능한 보상 경로)")
    void reaper_failsAbandonedTossPending_compensates() throws Exception {
        Checked c = checkout(2); // 재고 100 → 98
        MemberCoupon applied = issueAndUseCoupon(c.orderId(), 5_000L);
        Long memberCouponId = applied.getId();
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK - 2);

        // test 프로파일: min-age·toss-pending-timeout 모두 PT0S → 갓 접수된 toss-pending 도 즉시 만료 대상.
        reconciliationScheduler.reconcilePendingPayments();

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK); // 재고 복원

        MemberCoupon restored = memberCouponRepository.findById(memberCouponId).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(restored.getOrderId()).isNull();
    }

    @Test
    @DisplayName("successUrl: 비숫자 amount 등 파라미터 오류 → JSON 누출 없이 302 fail, 결제 PENDING 유지")
    void success_malformedAmount_redirectsFail() throws Exception {
        Checked c = checkout(1);

        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", c.paymentKey())
                        .param("orderId", c.orderNumber())
                        .param("amount", "not-a-number"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/" + c.orderNumber() + "?payment=fail"));

        assertThat(paymentStatus(c.orderId())).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("successUrl: 알 수 없는 주문 → 404 JSON 이 아니라 302 fail 로 안내")
    void success_unknownOrder_redirectsFail() throws Exception {
        mockMvc.perform(get("/payments/toss/success")
                        .param("paymentKey", "pk-x")
                        .param("orderId", "ORD-20991231-ZZZZZZ")
                        .param("amount", "10000"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/ORD-20991231-ZZZZZZ?payment=fail"));
    }

    @Test
    @DisplayName("failUrl: 알 수 없는 주문 → 404 JSON 이 아니라 302 fail 로 안내")
    void fail_unknownOrder_redirectsFail() throws Exception {
        mockMvc.perform(get("/payments/toss/fail")
                        .param("code", "PAY_PROCESS_CANCELED")
                        .param("message", "취소")
                        .param("orderId", "ORD-20991231-ZZZZZZ"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/orders/ORD-20991231-ZZZZZZ?payment=fail"));
    }

    /** 쿠폰을 발급해 주문에 사용(USED) 상태로 만든다. */
    private MemberCoupon issueAndUseCoupon(Long orderId, long discount) {
        Coupon coupon = couponRepository.saveAndFlush(
                Coupon.builder("정액-" + discount, CouponDiscountType.FIXED_AMOUNT, discount,
                                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS))
                        .build());
        MemberCoupon memberCoupon = MemberCoupon.issue(coupon, memberId, Instant.now());
        memberCoupon.use(orderId, Instant.now());
        return memberCouponRepository.saveAndFlush(memberCoupon);
    }
}
