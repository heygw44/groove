package com.groove.payment.application;

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
import com.groove.member.domain.MemberRepository;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 콜백 적용의 비관락 직렬화 검증 — 멱등 계층을 우회한 채 applyResult 를 여러 스레드로 직접 동시 호출한다.
 * findByPgTransactionIdForUpdate(PESSIMISTIC_WRITE) 로 동시 콜백이 직렬화되고 패자는 종착 상태를 읽어
 * ALREADY_PROCESSED 로 흡수되므로, 상태 전이·보상(재고 복원)은 정확히 1회만 일어난다.
 * @Transactional 이 아니라 셋업 save 가 커밋돼 동시 트랜잭션에서 보인다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("결제 콜백 비관락 동시성 테스트")
class PaymentCallbackConcurrencyTest {

    private static final int INITIAL_STOCK = 100;
    private static final int CONCURRENT_CALLS = 8;
    private static final int THREAD_POOL_SIZE = 8;

    @Autowired private PaymentCallbackService callbackService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private com.groove.common.outbox.OutboxEventRepository outboxEventRepository;

    private int seq;
    private Long memberId;
    private Long albumId;

    @BeforeEach
    void setUp() {
        // FK 의존 순서: outbox → payment → orders → album → artist/genre/label, member.
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
    }

    private record Created(Long paymentId, Long orderId, String pgTransactionId) {
    }

    /** 재고를 quantity 만큼 차감하고 PENDING 주문 + PENDING 결제를 직접 영속화한다. */
    private Created createPendingPayment(int quantity) {
        Album album = albumRepository.findById(albumId).orElseThrow();
        album.adjustStock(-quantity);
        albumRepository.saveAndFlush(album);

        String orderNumber = String.format("ORD-20260512-K%05d", ++seq);
        Order order = Order.placeForMember(orderNumber, memberId, OrderFixtures.sampleShippingInfo());
        order.addItem(OrderItem.create(album, quantity));
        Long orderId = orderRepository.saveAndFlush(order).getId();

        String pgTransactionId = "mock-tx-" + orderNumber;
        Payment payment = Payment.initiate(order, order.getTotalAmount(), PaymentMethod.CARD, "MOCK", pgTransactionId);
        Long paymentId = paymentRepository.saveAndFlush(payment).getId();
        return new Created(paymentId, orderId, pgTransactionId);
    }

    /** applyResult 를 CONCURRENT_CALLS 회 동시 호출하고 (outcome 모음, 예외 모음) 을 반환한다. */
    private Outcomes fireConcurrent(String pgTransactionId, PaymentStatus result, String failureReason)
            throws InterruptedException {
        ConcurrentLinkedQueue<PaymentCallbackResult.Outcome> outcomes = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ConcurrencyHarness.runConcurrently(THREAD_POOL_SIZE, CONCURRENT_CALLS, i -> {
            try {
                outcomes.add(callbackService.applyResult(pgTransactionId, result, failureReason).outcome());
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        return new Outcomes(outcomes, errors);
    }

    private record Outcomes(ConcurrentLinkedQueue<PaymentCallbackResult.Outcome> outcomes,
                            ConcurrentLinkedQueue<Throwable> errors) {

        long count(PaymentCallbackResult.Outcome outcome) {
            return outcomes.stream().filter(o -> o == outcome).count();
        }
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
    @DisplayName("동시 PAID 콜백 8건 → 예외 0, APPLIED 정확히 1·나머지 ALREADY_PROCESSED, 결제·주문 단일 전이")
    void concurrentPaidCallbacks_serializedByLock() throws Exception {
        Created c = createPendingPayment(1);

        Outcomes r = fireConcurrent(c.pgTransactionId(), PaymentStatus.PAID, null);

        assertThat(r.errors()).isEmpty(); // IllegalStateException 없음
        assertThat(r.count(PaymentCallbackResult.Outcome.APPLIED)).isEqualTo(1);
        assertThat(r.count(PaymentCallbackResult.Outcome.ALREADY_PROCESSED)).isEqualTo(CONCURRENT_CALLS - 1);
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.PAID);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("동시 FAILED 콜백 8건 → 예외 0, APPLIED 1·나머지 ALREADY_PROCESSED, 재고 복원 정확히 1회")
    void concurrentFailedCallbacks_restoreStockOnce() throws Exception {
        Created c = createPendingPayment(2); // 재고 100 → 98

        Outcomes r = fireConcurrent(c.pgTransactionId(), PaymentStatus.FAILED, "한도 초과");

        assertThat(r.errors()).isEmpty();
        assertThat(r.count(PaymentCallbackResult.Outcome.APPLIED)).isEqualTo(1);
        assertThat(r.count(PaymentCallbackResult.Outcome.ALREADY_PROCESSED)).isEqualTo(CONCURRENT_CALLS - 1);
        assertThat(paymentStatus(c.paymentId())).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus(c.orderId())).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(currentStock()).isEqualTo(INITIAL_STOCK); // 98 + 2, 정확히 1회만 복원
    }
}
