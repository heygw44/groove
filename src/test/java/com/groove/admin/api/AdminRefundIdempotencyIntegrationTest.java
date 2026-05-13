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
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.mock.MockPaymentGateway;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보상 트랜잭션 부분 실패 시 PG 환불 멱등 키(#72) 의 효과를 검증하는 통합 테스트.
 *
 * <h2>시나리오</h2>
 * <ol>
 *   <li>앨범 재고를 {@code Integer.MAX_VALUE} 로 세팅 + PAID 주문(qty=2) + PAID 결제.</li>
 *   <li><b>1차 환불 호출</b>: {@code paymentGateway.refund()} 성공 → {@code payment.markRefunded()} 성공 →
 *       {@code order.changeStatus(CANCELLED)} 성공 → {@code album.adjustStock(+2)} 가 오버플로로
 *       {@link com.groove.catalog.album.exception.IllegalStockAdjustmentException} → 트랜잭션 전체 롤백.
 *       이 시점 PG 측엔 첫 환불 응답이 캐시되어 있다 (실세계 PG 라면 환불이 확정되어 있을 것).</li>
 *   <li>재고를 정상 범위로 조정해 보상 트랜잭션 재시도가 성공할 수 있도록 만든다.</li>
 *   <li><b>2차 환불 호출 (재시도)</b>: 같은 결제 → 같은 멱등 키 →
 *       {@code MockPaymentGateway} 가 캐시 응답을 그대로 반환 ({@code refundCallCount()} 증가 없음).
 *       DB 작업은 정상 완료.</li>
 *   <li>검증: PG 실호출 카운터 == 1, payment REFUNDED, order CANCELLED, 재고 복원 정상 반영.</li>
 * </ol>
 *
 * <p>본 테스트가 깨지면 PG 가 같은 결제에 두 번 환불 요청을 받는다는 뜻이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("관리자 환불 멱등 키 — 보상 트랜잭션 재시도 시 PG 중복 환불 방지 (#72)")
class AdminRefundIdempotencyIntegrationTest {

    private static final long UNIT_PRICE = 30_000L;
    private static final int QTY = 2;

    @Autowired
    private MockMvc mockMvc;
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
    @Autowired
    private PaymentGateway paymentGateway;

    private String adminBearer;
    private Long memberId;
    private Long albumId;

    @BeforeEach
    void setUp() {
        clearAll();
        Member m = memberRepository.saveAndFlush(Member.register("admin72@example.com", "$2a$10$dummy", "M", "01000000001"));
        memberId = m.getId();

        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist", null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock"));
        Label label = labelRepository.saveAndFlush(Label.create("Label"));
        // 1차 환불에서 adjustStock(+2) 가 오버플로 → IllegalStockAdjustmentException 으로 롤백을 유도하기 위한 초기치.
        Album album = albumRepository.saveAndFlush(Album.create("Album", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, UNIT_PRICE, Integer.MAX_VALUE,
                AlbumStatus.SELLING, false, null, null));
        albumId = album.getId();

        adminBearer = "Bearer " + jwtProvider.issueAccessToken(999L, MemberRole.ADMIN);
    }

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

    private String persistPaidOrderWithPayment() {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = OrderFixtures.memberOrder("ORD-20260513-IDEM01", memberId);
        order.addItem(OrderItem.create(album, QTY));
        order.changeStatus(OrderStatus.PAID, null);
        Order saved = orderRepository.saveAndFlush(order);

        Payment payment = Payment.initiate(saved, saved.getTotalAmount(), PaymentMethod.CARD, "MOCK",
                "mock-tx-IDEM01");
        payment.markPaid();
        paymentRepository.saveAndFlush(payment);
        return saved.getOrderNumber();
    }

    @Test
    @DisplayName("PG 호출 후 재고 복원 실패 → 재시도 시 PG 실호출 1회 + 최종 DB 정합 (#72)")
    void compensatingFailure_thenRetry_pgCalledOnce() throws Exception {
        // given — Integer.MAX_VALUE 재고 위에 qty=2 PAID 주문/결제
        String orderNumber = persistPaidOrderWithPayment();
        MockPaymentGateway mockGateway = (MockPaymentGateway) paymentGateway;
        int callsBefore = mockGateway.refundCallCount();

        // 1차 환불 — adjustStock(+2) 가 오버플로로 IllegalStockAdjustmentException → 트랜잭션 롤백 → 400 응답.
        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"오버플로 유발\"}"))
                .andExpect(status().isBadRequest());

        // 1차 호출로 PG 측엔 환불이 1건 기록되었지만 DB 는 롤백되어 PAID/PAID 가 유지된다.
        Order rolledBack = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(rolledBack.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findByOrderId(rolledBack.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
        assertThat(mockGateway.refundCallCount()).isEqualTo(callsBefore + 1);

        // 재시도 전 — 재고 복원이 정상 범위가 되도록 앨범 재고를 합리적인 값으로 낮춘다.
        Album album = albumRepository.findById(albumId).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(album, "stock", 10);
        albumRepository.saveAndFlush(album);

        // 2차 환불 (재시도) — 같은 결제 → 같은 멱등 키 → MockPaymentGateway 가 캐시 응답 반환 (실호출 X).
        mockMvc.perform(post("/api/v1/admin/orders/{n}/refund", orderNumber)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"재시도\"}"))
                .andExpect(status().isOk());

        // 검증 — PG 실호출 카운터는 1차 호출에서만 증가했고 (총 +1), DB 는 정상 완료 상태.
        assertThat(mockGateway.refundCallCount())
                .as("같은 멱등 키 재호출 → PG 실호출 추가 없음")
                .isEqualTo(callsBefore + 1);
        Order finalOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(finalOrder.getCancelledReason()).isEqualTo("재시도");
        assertThat(paymentRepository.findByOrderId(finalOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(albumRepository.findById(albumId).orElseThrow().getStock())
                .isEqualTo(10 + QTY); // 재시도 트랜잭션의 재고 복원만 반영 (1차는 롤백되어 없음)
    }
}
