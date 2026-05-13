package com.groove.admin.api;

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
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.support.OrderFixtures;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
@DisplayName("/api/v1/admin/orders (ADMIN 주문 조회 / 상태 강제 전환 / 환불)")
class AdminOrderControllerTest {

    private static final long UNIT_PRICE = 30_000L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
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

    private final AtomicInteger orderSeq = new AtomicInteger();

    private String adminBearer;
    private String userBearer;
    private Long memberA;
    private Long memberB;
    private Long albumId;

    @BeforeEach
    void setUp() {
        clearAll();

        Member a = memberRepository.saveAndFlush(Member.register("a@example.com", "$2a$10$dummy", "A", "01000000001"));
        Member b = memberRepository.saveAndFlush(Member.register("b@example.com", "$2a$10$dummy", "B", "01000000002"));
        memberA = a.getId();
        memberB = b.getId();

        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        Label label = labelRepository.saveAndFlush(Label.create("Label"));
        albumId = albumRepository.saveAndFlush(Album.create("Album", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, UNIT_PRICE, 100, AlbumStatus.SELLING, false, null, null)).getId();

        adminBearer = "Bearer " + jwtProvider.issueAccessToken(999L, MemberRole.ADMIN);
        userBearer = "Bearer " + jwtProvider.issueAccessToken(memberA, MemberRole.USER);
    }

    /**
     * 알파벳 순서상 본 클래스가 가장 먼저 실행돼 데이터가 잔류하면, 이어지는 카탈로그 테스트
     * ({@code AlbumQueryControllerTest} 등) 의 {@code albumRepository.deleteAllInBatch()} 가
     * {@code fk_order_item_album} RESTRICT 에 걸린다 — 시작/종료 양쪽에서 청소해 격리한다.
     */
    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    private String nextOrderNumber() {
        return "ORD-20260513-" + String.format("%06d", orderSeq.incrementAndGet());
    }

    private List<OrderStatus> pathTo(OrderStatus target) {
        return switch (target) {
            case PENDING -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case PREPARING -> List.of(OrderStatus.PAID, OrderStatus.PREPARING);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            default -> throw new IllegalArgumentException("지원하지 않는 target: " + target);
        };
    }

    /** memberId 소유 + 라인 1개(qty) + targetStatus 로 전이된 주문을 영속화하고 orderNumber 를 반환한다. */
    private String persistOrder(Long memberId, int qty, OrderStatus targetStatus) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = OrderFixtures.memberOrder(nextOrderNumber(), memberId);
        order.addItem(OrderItem.create(album, qty));
        pathTo(targetStatus).forEach(s -> order.changeStatus(s, null));
        return orderRepository.saveAndFlush(order).getOrderNumber();
    }

    /** 게스트 주문(라인 1개, PENDING)을 영속화하고 orderNumber 를 반환한다. */
    private String persistGuestOrder(String guestEmail, int qty) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = OrderFixtures.guestOrder(nextOrderNumber(), guestEmail, "01099999999");
        order.addItem(OrderItem.create(album, qty));
        return orderRepository.saveAndFlush(order).getOrderNumber();
    }

    /** 위 주문에 PAID 결제를 붙인다. */
    private void persistPaidPayment(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        Payment payment = Payment.initiate(order, order.getTotalAmount(), PaymentMethod.CARD, "MOCK",
                "mock-tx-" + orderNumber);
        payment.markPaid();
        paymentRepository.saveAndFlush(payment);
    }

    private void persistPendingPayment(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        paymentRepository.saveAndFlush(Payment.initiate(order, order.getTotalAmount(), PaymentMethod.CARD, "MOCK",
                "mock-tx-" + orderNumber));
    }

    private int albumStock() {
        return albumRepository.findById(albumId).orElseThrow().getStock();
    }

    // ---------------------------------------------------------------------
    // 인가
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET 목록 미인증 → 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET 목록 USER 권한 → 403")
    void list_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET 상세 USER 권한 → 403")
    void detail_userRole_returns403() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PENDING);
        mockMvc.perform(get("/api/v1/admin/orders/{n}", orderNumber).header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH 상태 USER 권한 → 403 + 상태 불변")
    void status_userRole_returns403() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);
        mockMvc.perform(patch("/api/v1/admin/orders/{n}/status", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"PREPARING\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(orderRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("POST 환불 USER 권한 → 403 + 결제 불변")
    void refund_userRole_returns403() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);
        persistPaidPayment(orderNumber);
        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        assertThat(paymentRepository.findByOrderId(
                orderRepository.findByOrderNumber(orderNumber).orElseThrow().getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    // ---------------------------------------------------------------------
    // 목록 / 상세
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET 목록 → 전체 주문 페이징")
    void list_returnsAll() throws Exception {
        persistOrder(memberA, 1, OrderStatus.PENDING);
        persistOrder(memberA, 1, OrderStatus.PAID);
        persistOrder(memberB, 1, OrderStatus.PENDING);

        mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].orderNumber").exists())
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
    }

    @Test
    @DisplayName("GET 목록 ?status=PAID → 해당 상태만")
    void list_filterByStatus() throws Exception {
        persistOrder(memberA, 1, OrderStatus.PENDING);
        String paid = persistOrder(memberA, 1, OrderStatus.PAID);

        mockMvc.perform(get("/api/v1/admin/orders").param("status", "PAID")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderNumber").value(paid))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }

    @Test
    @DisplayName("GET 목록 ?memberId=B → 해당 회원 주문만")
    void list_filterByMember() throws Exception {
        persistOrder(memberA, 1, OrderStatus.PENDING);
        persistOrder(memberA, 1, OrderStatus.PENDING);
        String bOrder = persistOrder(memberB, 1, OrderStatus.PENDING);

        mockMvc.perform(get("/api/v1/admin/orders").param("memberId", String.valueOf(memberB))
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderNumber").value(bOrder))
                .andExpect(jsonPath("$.content[0].memberId").value(memberB));
    }

    @Test
    @DisplayName("GET 목록 게스트 주문 → guestEmail 노출, memberId 없음")
    void list_guestOrder_exposesGuestEmail() throws Exception {
        persistGuestOrder("guest@example.com", 1);

        mockMvc.perform(get("/api/v1/admin/orders").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].guestEmail").value("guest@example.com"))
                .andExpect(jsonPath("$.content[0].memberId").doesNotExist());
    }

    @Test
    @DisplayName("GET 목록 ?from/?to → 생성 시각 범위 필터")
    void list_filterByPeriod() throws Exception {
        persistOrder(memberA, 1, OrderStatus.PENDING);

        mockMvc.perform(get("/api/v1/admin/orders").param("from", "2000-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/admin/orders").param("from", "2999-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET 목록 허용되지 않은 정렬 키 → 400")
    void list_invalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders").param("sort", "totalAmount,desc")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET 상세 → 200 + 라인/배송지/소유자")
    void detail_returns200() throws Exception {
        String orderNumber = persistOrder(memberA, 2, OrderStatus.PAID);

        mockMvc.perform(get("/api/v1/admin/orders/{n}", orderNumber).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.memberId").value(memberA))
                .andExpect(jsonPath("$.totalAmount").value((int) (UNIT_PRICE * 2)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.shipping.recipientName").exists());
    }

    @Test
    @DisplayName("GET 상세 미존재 → 404 ORDER_NOT_FOUND")
    void detail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/{n}", "ORD-20260513-ZZZ999")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET 상세 잘못된 orderNumber 형식 → 400")
    void detail_invalidFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/{n}", "not-an-order")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // 상태 강제 전환
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("PATCH 상태 합법 전이(PAID→PREPARING) → 200 + DB 반영")
    void status_legalTransition_returns200() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);

        mockMvc.perform(patch("/api/v1/admin/orders/{n}/status", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("target", "PREPARING", "reason", "출고 준비"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));

        assertThat(orderRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("PATCH 상태 불법 전이(PAID→SHIPPED) → 409 ORDER_INVALID_STATE_TRANSITION + 불변")
    void status_illegalTransition_returns409() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);

        mockMvc.perform(patch("/api/v1/admin/orders/{n}/status", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"SHIPPED\",\"reason\":\"건너뛰기\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATE_TRANSITION"));

        assertThat(orderRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PATCH 상태 지원하지 않는 대상(CANCELLED) → 422 DOMAIN_RULE_VIOLATION")
    void status_unsupportedTarget_returns422() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);

        mockMvc.perform(patch("/api/v1/admin/orders/{n}/status", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"CANCELLED\",\"reason\":\"환불\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_003"));
    }

    @Test
    @DisplayName("PATCH 상태 사유 누락 → 400")
    void status_missingReason_returns400() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);

        mockMvc.perform(patch("/api/v1/admin/orders/{n}/status", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"PREPARING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH 상태 미존재 주문 → 404")
    void status_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/orders/{n}/status", "ORD-20260513-ZZZ999")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"PREPARING\",\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------
    // 환불
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("POST 환불 (PAID 주문 + PAID 결제) → 200 + Payment REFUNDED + Order CANCELLED + 재고 복원")
    void refund_paidOrder_returns200() throws Exception {
        String orderNumber = persistOrder(memberA, 2, OrderStatus.PAID);
        persistPaidPayment(orderNumber);
        int stockBefore = albumStock();

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"고객 변심\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.alreadyRefunded").value(false))
                .andExpect(jsonPath("$.refundedAt").exists());

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledReason()).isEqualTo("고객 변심");
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(albumStock()).isEqualTo(stockBefore + 2);
    }

    @Test
    @DisplayName("환불 후 GET 상세 → cancelledReason / cancelledAt 노출")
    void detail_afterRefund_includesCancelledFields() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);
        persistPaidPayment(orderNumber);

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"오배송 보상\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/orders/{n}", orderNumber).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledReason").value("오배송 보상"))
                .andExpect(jsonPath("$.cancelledAt").exists());
    }

    @Test
    @DisplayName("POST 환불 중복 요청 → 200 alreadyRefunded=true, 재고 중복 복원 없음")
    void refund_idempotent() throws Exception {
        String orderNumber = persistOrder(memberA, 2, OrderStatus.PAID);
        persistPaidPayment(orderNumber);
        int stockBefore = albumStock();

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRefunded").value(false));

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRefunded").value(true))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        assertThat(albumStock()).isEqualTo(stockBefore + 2);
    }

    @Test
    @DisplayName("POST 환불 결제 없음 → 404 PAYMENT_NOT_FOUND")
    void refund_noPayment_returns404() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PAID);

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST 환불 PENDING 결제 → 409 PAYMENT_NOT_REFUNDABLE + 불변")
    void refund_pendingPayment_returns409() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.PENDING);
        persistPendingPayment(orderNumber);
        int stockBefore = albumStock();

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_REFUNDABLE"));

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(albumStock()).isEqualTo(stockBefore);
    }

    @Test
    @DisplayName("POST 환불 배송 시작(SHIPPED) 주문 → 409 ORDER_INVALID_STATE_TRANSITION + 결제 불변")
    void refund_shippedOrder_returns409() throws Exception {
        String orderNumber = persistOrder(memberA, 1, OrderStatus.SHIPPED);
        persistPaidPayment(orderNumber);

        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_STATE_TRANSITION"));

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(paymentRepository.findByOrderId(order.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("POST 환불 미존재 주문 → 404 ORDER_NOT_FOUND")
    void refund_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", "ORD-20260513-ZZZ999")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
