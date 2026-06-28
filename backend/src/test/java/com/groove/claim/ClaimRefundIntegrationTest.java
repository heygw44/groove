package com.groove.claim;

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
import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.claim.application.ClaimCreateCommand;
import com.groove.claim.application.ClaimService;
import com.groove.claim.application.OrderPartialCancelCommand;
import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.domain.ClaimType;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
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
import com.groove.shipping.domain.ShippingRepository;
import com.groove.support.ClaimFixtures;
import com.groove.support.MemberFixtures;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 반품 환불 전 과정 통합 테스트. 부분→전량 누적 정합 + claim 별 PG 멱등(실호출 1회).
 * ClaimService 단계 위임 메서드를 직접 호출해 INSPECTING 까지 민 뒤 환불을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("반품 환불 통합 — 부분→전량 정합 + claim 별 PG 멱등 (#239)")
class ClaimRefundIntegrationTest {

    private static final long UNIT_PRICE = 15_000L;

    @Autowired
    private ClaimService claimService;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ShippingRepository shippingRepository;
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
    private PaymentGateway paymentGateway;

    private Long memberId;
    private Long albumId;
    private int seq;

    @BeforeEach
    void setUp() {
        clearAll();
        Member m = memberRepository.saveAndFlush(
                MemberFixtures.register("claim-int@example.com", "$2a$10$dummy", "회원", "01000000001"));
        memberId = m.getId();
        String uniq = "-" + System.nanoTime();
        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist" + uniq, null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock" + uniq));
        Label label = labelRepository.saveAndFlush(Label.create("Label" + uniq));
        Album album = albumRepository.saveAndFlush(Album.create("Album", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, UNIT_PRICE, 100, AlbumStatus.SELLING, false, null, null));
        albumId = album.getId();
    }

    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        // 자식(FK 참조)부터: refresh_token/claim/shipping/payment → orders/member.
        refreshTokenRepository.deleteAllInBatch();
        claimRepository.deleteAllInBatch();
        shippingRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
        labelRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    // --- 헬퍼 ----------------------------------------------------------------

    private Order persistDeliveredOrder(int qty) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        return ClaimFixtures.persistDeliveredOrder(orderRepository, paymentRepository, shippingRepository,
                album, memberId, qty, (++seq) + "-" + System.nanoTime());
    }

    /** 발송 전(PAID) 주문 + PAID 결제(배송행 없음). 부분 취소(CANCEL) 대상. */
    private Order persistPaidOrder(int qty) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = OrderFixtures.memberOrder("ORD-CLM-" + (++seq) + "-" + System.nanoTime(), memberId);
        order.addItem(OrderItem.create(album, qty));
        order.changeStatus(OrderStatus.PAID, null, Instant.now());
        Order saved = orderRepository.saveAndFlush(order);

        Payment payment = Payment.initiate(saved, saved.getPayableAmount(), PaymentMethod.CARD, "MOCK",
                "mock-tx-" + seq + "-" + System.nanoTime());
        payment.markPaid(Instant.now());
        paymentRepository.saveAndFlush(payment);
        return saved;
    }

    private Long requestClaim(String orderNumber, Long orderItemId, int quantity) {
        ClaimCreateCommand command = new ClaimCreateCommand(memberId, orderNumber, "단순 변심",
                List.of(new ClaimCreateCommand.Line(orderItemId, quantity)));
        return claimService.request(command).getId();
    }

    private void driveToInspecting(Long claimId) {
        claimService.approve(claimId);
        claimService.advanceToInTransit(claimId);
        claimService.advanceToInspecting(claimId);
    }

    // --- 테스트 --------------------------------------------------------------

    @Test
    @DisplayName("부분 반품 후 전량 반품 — 결제 PARTIALLY_REFUNDED→REFUNDED, 재고 누적 복원, 전량 시 returned_at")
    void partialThenFull_accumulatesRefundAndRestock() {
        Order order = persistDeliveredOrder(2); // total/payable = 30000
        Long orderItemId = order.getItems().get(0).getId();
        Long orderId = order.getId();
        int stockBefore = albumRepository.findById(albumId).orElseThrow().getStock();

        // 1) 부분 반품 (1/2)
        Long claimA = requestClaim(order.getOrderNumber(), orderItemId, 1);
        driveToInspecting(claimA);
        claimService.completeRefund(claimA);

        Payment afterPartial = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(afterPartial.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(afterPartial.getRefundedAmount()).isEqualTo(UNIT_PRICE);
        assertThat(albumRepository.findById(albumId).orElseThrow().getStock()).isEqualTo(stockBefore + 1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getReturnedAt()).isNull();

        // 2) 나머지 반품 (1/2) → 전량
        Long claimB = requestClaim(order.getOrderNumber(), orderItemId, 1);
        driveToInspecting(claimB);
        claimService.completeRefund(claimB);

        Payment afterFull = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(afterFull.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(afterFull.getRefundedAmount()).isEqualTo(2 * UNIT_PRICE);
        assertThat(albumRepository.findById(albumId).orElseThrow().getStock()).isEqualTo(stockBefore + 2);
        assertThat(orderRepository.findById(orderId).orElseThrow().getReturnedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 보상 트랜잭션 부분 실패 후 재시도 — claim 별 멱등 키로 PG 실호출 1회 (#72/#239)")
    void compensatingFailure_thenRetry_pgCalledOncePerClaim() {
        Order order = persistDeliveredOrder(2);
        Long orderItemId = order.getItems().get(0).getId();
        Long orderId = order.getId();
        Long claimId = requestClaim(order.getOrderNumber(), orderItemId, 2); // 전량 반품
        driveToInspecting(claimId);

        MockPaymentGateway mock = (MockPaymentGateway) paymentGateway;
        int callsBefore = mock.refundCallCount();

        // 재고를 Integer.MAX_VALUE 로 — 재입고 adjustStock(+2)가 오버플로로 실패.
        Album album = albumRepository.findById(albumId).orElseThrow();
        ReflectionTestUtils.setField(album, "stock", Integer.MAX_VALUE);
        albumRepository.saveAndFlush(album);

        // 1차 환불 — PG 호출 후 재입고 실패 → 트랜잭션 롤백.
        assertThatThrownBy(() -> claimService.completeRefund(claimId)).isInstanceOf(RuntimeException.class);

        // claim INSPECTING / payment PAID 유지, PG 측엔 1건 기록.
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(mock.refundCallCount()).isEqualTo(callsBefore + 1);

        // 재고를 정상값으로 낮춤.
        Album fixed = albumRepository.findById(albumId).orElseThrow();
        ReflectionTestUtils.setField(fixed, "stock", 10);
        albumRepository.saveAndFlush(fixed);

        // 2차 환불 (재시도) — 같은 claim → 같은 멱등 키 → PG 실호출 추가 없음.
        Claim result = claimService.completeRefund(claimId);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(mock.refundCallCount()).as("같은 claim 재시도 → PG 실호출 추가 없음").isEqualTo(callsBefore + 1);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(albumRepository.findById(albumId).orElseThrow().getStock()).isEqualTo(10 + 2);
    }

    @Test
    @DisplayName("부분 취소 후 전량 취소 — 결제 PARTIALLY_REFUNDED→REFUNDED, 재고 누적 복원, 전량 시 주문 CANCELLED (#238)")
    void cancelPartially_thenFull() {
        Order order = persistPaidOrder(2); // total/payable = 30000
        Long orderItemId = order.getItems().get(0).getId();
        Long orderId = order.getId();
        int stockBefore = albumRepository.findById(albumId).orElseThrow().getStock();

        // 1) 부분 취소 (1/2) — 발송 전 CANCEL 클레임, 즉시 환불.
        Claim partial = claimService.cancelPartially(new OrderPartialCancelCommand(order.getOrderNumber(), "부분 취소",
                List.of(new ClaimCreateCommand.Line(orderItemId, 1))));
        assertThat(partial.getClaimType()).isEqualTo(ClaimType.CANCEL);
        assertThat(partial.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(partial.getRefundAmount()).isEqualTo(UNIT_PRICE);

        Payment afterPartial = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(afterPartial.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(afterPartial.getRefundedAmount()).isEqualTo(UNIT_PRICE);
        assertThat(albumRepository.findById(albumId).orElseThrow().getStock()).isEqualTo(stockBefore + 1);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);

        // 2) 잔여 전량 취소 (1/1) → 누적이 전량에 도달.
        Claim full = claimService.cancelPartially(new OrderPartialCancelCommand(order.getOrderNumber(), "잔여 취소",
                List.of(new ClaimCreateCommand.Line(orderItemId, 1))));
        assertThat(full.getStatus()).isEqualTo(ClaimStatus.REFUNDED);

        Payment afterFull = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(afterFull.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(afterFull.getRefundedAmount()).isEqualTo(2 * UNIT_PRICE);
        assertThat(albumRepository.findById(albumId).orElseThrow().getStock()).isEqualTo(stockBefore + 2);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("발송 전 부분취소 → 배송완료 후 잔여 반품(교차 타입) — 반품 환불이 정가로 정확, 누적 결제 전액 (#238 리뷰)")
    void cancelPreShip_thenReturnRemaining_crossType() {
        Order order = persistPaidOrder(2); // total/payable 30000, 무쿠폰
        Long orderItemId = order.getItems().get(0).getId();
        Long orderId = order.getId();

        // 1) 발송 전 1개 부분취소(CANCEL) → 결제 PARTIALLY_REFUNDED 15000.
        claimService.cancelPartially(new OrderPartialCancelCommand(order.getOrderNumber(), "부분 취소",
                List.of(new ClaimCreateCommand.Line(orderItemId, 1))));
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getRefundedAmount()).isEqualTo(UNIT_PRICE);

        // 2) 잔여 1개를 배송완료까지 전이해 반품 자격(DELIVERED) 확보. 배송 행 없이 강제 전이해도
        //    changeStatus(DELIVERED)가 order.deliveredAt 을 기록하므로 반품 기한 anchor 가 결정된다.
        Order live = orderRepository.findById(orderId).orElseThrow();
        live.changeStatus(OrderStatus.PREPARING, null, Instant.now());
        live.changeStatus(OrderStatus.SHIPPED, null, Instant.now());
        live.changeStatus(OrderStatus.DELIVERED, null, Instant.now());
        orderRepository.saveAndFlush(live);

        // 3) 잔여 1개 반품(RETURN) — 클레임된 취소 1개 차감해 반품가능 수량 1.
        Long returnClaimId = requestClaim(order.getOrderNumber(), orderItemId, 1);
        driveToInspecting(returnClaimId);
        Claim refunded = claimService.completeRefund(returnClaimId);

        // 반품 환불은 정가 15000(무쿠폰), 결제는 전액(30000) REFUNDED.
        assertThat(refunded.getRefundAmount()).isEqualTo(UNIT_PRICE);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(2 * UNIT_PRICE);
    }
}
