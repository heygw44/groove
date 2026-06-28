package com.groove.order.concurrency;

import com.groove.auth.domain.RefreshTokenRepository;
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
import com.groove.claim.application.ClaimCreateCommand;
import com.groove.claim.application.ClaimService;
import com.groove.claim.application.OrderPartialCancelCommand;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.exception.OrderNotCancellableException;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.shipping.application.ShippingService;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.support.ConcurrencyHarness;
import com.groove.support.MemberFixtures;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 취소·배송 자동진행 lost update 회귀 (#316) — 주문 행 비관적 락. 동시 다중 취소가 한 PENDING 주문을
 * 경합해도 취소는 정확히 1건만 성공해 재고·쿠폰을 1회만 복원하고(2배 복원 방지), 배송 자동진행과 전량 취소가
 * 동시에 들어와도 최종 상태가 (SHIPPED,SHIPPED) 또는 (CANCELLED,CANCELLED) 중 하나로 정합함을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("주문 취소·배송 자동진행 동시성 (#316 비관적 락)")
class OrderCancelShippingRaceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelShippingRaceConcurrencyTest.class);

    private static final int INITIAL_STOCK = 100;
    private static final long UNIT_PRICE = 15_000L;

    @Autowired
    private OrderService orderService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private ShippingService shippingService;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ShippingRepository shippingRepository;
    @Autowired
    private ClaimRepository claimRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private LabelRepository labelRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Long memberId;
    private Long albumId;
    private int seq;

    @BeforeEach
    void setUp() {
        clearAll();
        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("order-race@example.com", "$2a$10$dummy", "회원", "01000000002"));
        memberId = member.getId();

        String uniq = "-" + System.nanoTime();
        Artist artist = artistRepository.saveAndFlush(Artist.create("Artist" + uniq, null));
        Genre genre = genreRepository.saveAndFlush(Genre.create("Rock" + uniq));
        Label label = labelRepository.saveAndFlush(Label.create("Label" + uniq));
        albumId = albumRepository.saveAndFlush(Album.create("Album", artist, genre, label,
                (short) 2026, AlbumFormat.LP_12, UNIT_PRICE, INITIAL_STOCK,
                AlbumStatus.SELLING, false, null, null)).getId();
    }

    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        // 자식(FK 참조)부터 정리.
        refreshTokenRepository.deleteAllInBatch();
        cartRepository.deleteAllInBatch();
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

    @Test
    @DisplayName("동시 취소 — 한 PENDING 주문에 다중 cancel → 정확히 1건 성공, 재고 1회만 복원 (2배 복원 방지)")
    void concurrentCancel_singlePendingOrder_restoresStockExactlyOnce() throws InterruptedException {
        int qty = 3;
        int stockBefore = currentStock();
        Order placed = orderService.place(memberId, singleAlbumOrder(qty));
        String orderNumber = placed.getOrderNumber();
        assertThat(currentStock()).as("주문 생성 시 재고 차감").isEqualTo(stockBefore - qty);

        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        int attempts = 8;
        ConcurrencyHarness.runConcurrently(attempts, attempts, i -> {
            try {
                orderService.cancel(memberId, orderNumber, "동시 취소");
                cancelled.incrementAndGet();
            } catch (IllegalStateTransitionException ex) {
                conflict.incrementAndGet(); // 락 후 CANCELLED 를 읽은 패자
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        int stockAfter = currentStock();
        log.info("[#316 동시취소] cancelled={}, conflict={}, other={}, stockBefore={}, stockAfter={}",
                cancelled.get(), conflict.get(), other.get(), stockBefore, stockAfter);

        assertThat(cancelled.get()).as("취소는 정확히 1건만 성공").isEqualTo(1);
        assertThat(conflict.get()).as("나머지는 이미 CANCELLED → IllegalStateTransitionException").isEqualTo(attempts - 1);
        assertThat(other.get()).as("데드락/기타 예외 0").isZero();
        assertThat(stockAfter).as("재고는 정확히 1회만 복원 — 2배 복원 아님").isEqualTo(stockBefore);
        assertThat(orderRepository.findByOrderNumber(orderNumber).orElseThrow().getStatus())
                .as("주문 최종 상태 CANCELLED").isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("배송 자동진행 vs 전량 취소 동시 — 최종 (SHIPPED,SHIPPED) 또는 (CANCELLED,CANCELLED) 로만 정합")
    void concurrentAdvanceAndFullCancel_orderAndShippingStayConsistent() throws InterruptedException {
        int qty = 2;
        PreparingFixture fx = persistPreparingOrderWithShipping(qty);

        AtomicInteger advanceDone = new AtomicInteger();
        AtomicInteger cancelDone = new AtomicInteger();
        AtomicInteger cancelRejected = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        ConcurrencyHarness.runConcurrently(2, 2, i -> {
            try {
                if (i == 0) {
                    shippingService.advanceToShipped(fx.shippingId());
                    advanceDone.incrementAndGet();
                } else {
                    claimService.cancelPartially(new OrderPartialCancelCommand(fx.orderNumber(), "전량 취소",
                            List.of(new ClaimCreateCommand.Line(fx.orderItemId(), qty))));
                    cancelDone.incrementAndGet();
                }
            } catch (OrderNotCancellableException ex) {
                cancelRejected.incrementAndGet(); // 배송이 먼저 SHIPPED 로 전진하면 취소는 자격 미달
            } catch (RuntimeException ex) {
                other.incrementAndGet();
                log.warn("예상 외 예외", ex);
            }
        });

        OrderStatus orderStatus = orderRepository.findByOrderNumber(fx.orderNumber()).orElseThrow().getStatus();
        ShippingStatus shippingStatus = shippingRepository.findByOrderId(fx.orderId()).orElseThrow().getStatus();
        log.info("[#316 진행vs취소] advanceDone={}, cancelDone={}, cancelRejected={}, other={}, order={}, shipping={}",
                advanceDone.get(), cancelDone.get(), cancelRejected.get(), other.get(), orderStatus, shippingStatus);

        boolean consistent =
                (orderStatus == OrderStatus.SHIPPED && shippingStatus == ShippingStatus.SHIPPED)
                        || (orderStatus == OrderStatus.CANCELLED && shippingStatus == ShippingStatus.CANCELLED);
        assertThat(consistent)
                .as("주문=%s, 배송=%s — (SHIPPED,SHIPPED) 또는 (CANCELLED,CANCELLED) 만 허용", orderStatus, shippingStatus)
                .isTrue();
        assertThat(orderStatus == OrderStatus.SHIPPED && shippingStatus == ShippingStatus.CANCELLED)
                .as("결제 환불 후 주문 SHIPPED·배송 CANCELLED 인 lost update 미발생").isFalse();
        assertThat(advanceDone.get()).as("배송 진행 호출은 예외 없이 완료").isEqualTo(1);
        assertThat(cancelDone.get() + cancelRejected.get()).as("취소는 성공 또는 자격미달 둘 중 하나로 귀결").isEqualTo(1);
        assertThat(other.get()).as("데드락/기타 예외 0").isZero();
    }

    /** PAID 결제 + PREPARING 주문 + PREPARING 배송행을 만든다. advanceToShipped 와 cancelPartially 양 경로의 자격을 동시에 만족. */
    private PreparingFixture persistPreparingOrderWithShipping(int qty) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        Order order = OrderFixtures.memberOrder("ORD-RACE-" + (++seq) + "-" + System.nanoTime(), memberId);
        order.addItem(OrderItem.create(album, qty));
        order.changeStatus(OrderStatus.PAID, null, Instant.now());
        order.changeStatus(OrderStatus.PREPARING, null, Instant.now());
        Order saved = orderRepository.saveAndFlush(order);
        Long orderItemId = saved.getItems().get(0).getId();

        Payment payment = Payment.initiate(saved, saved.getPayableAmount(), PaymentMethod.CARD, "MOCK",
                "mock-tx-" + seq + "-" + System.nanoTime());
        payment.markPaid(Instant.now());
        paymentRepository.saveAndFlush(payment);

        Shipping shipping = Shipping.prepare(saved, saved.getShippingInfo(), "trk-" + seq + "-" + System.nanoTime());
        Long shippingId = shippingRepository.saveAndFlush(shipping).getId();
        return new PreparingFixture(saved.getId(), saved.getOrderNumber(), orderItemId, shippingId);
    }

    private int currentStock() {
        return albumRepository.findById(albumId).orElseThrow().getStock();
    }

    private OrderCreateRequest singleAlbumOrder(int qty) {
        return new OrderCreateRequest(
                List.of(new OrderItemRequest(albumId, qty)),
                null,
                OrderFixtures.sampleShippingInfoRequest(),
                null);
    }

    private record PreparingFixture(Long orderId, String orderNumber, Long orderItemId, Long shippingId) {
    }
}
