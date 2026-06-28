package com.groove.coupon.application;

import com.groove.admin.application.AdminOrderService;
import com.groove.admin.application.RefundResult;
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
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponDiscountType;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.domain.MemberCouponStatus;
import com.groove.coupon.exception.CouponMinOrderNotMetException;
import com.groove.coupon.exception.CouponNotApplicableException;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.order.api.dto.GuestInfoRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.application.PaymentService;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.support.OrderFixtures;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쿠폰을 적용한 주문 → 결제 → 취소·환불 복원의 통합 E2E. 실 DB(Testcontainers MySQL)에서 PG 청구액이 할인
 * 반영된 금액인지(payment.amount == order.payableAmount), cancel·refund 두 경로 모두 쿠폰을 ISSUED 로
 * 복원하는지를 서비스 빈 직접 호출로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
// auto-webhook=false: refundPaidOrder_restoresCoupon 이 결제를 수동으로 markPaid 하므로,
// 비동기 PAID 웹훅이 같은 결제를 동시에 전이시키며 경합(콜백의 FOR UPDATE 락 ↔ 수동 갱신)하는 것을 막는다.
@TestPropertySource(properties = "payment.mock.auto-webhook=false")
@Import(TestcontainersConfig.class)
@DisplayName("쿠폰 주문/결제/취소/환불 통합 (#91)")
class CouponOrderIntegrationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AdminOrderService adminOrderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private MemberCouponRepository memberCouponRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private GenreRepository genreRepository;
    @Autowired
    private LabelRepository labelRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    // 픽스처 사전 커밋용. 같은 타입 빈이 여럿이라 트랜잭션 매니저로부터 직접 만든다(기본 REQUIRED).
    private TransactionTemplate txTemplate;

    @Autowired
    void wireTxTemplate(PlatformTransactionManager transactionManager) {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    private record Fixtures(Member member, Album album, Coupon coupon, MemberCoupon memberCoupon) { }

    private Fixtures setupFixtures(long unitPrice, int stock,
                                    CouponDiscountType type, long discountValue, long minOrder,
                                    Long maxDiscount) {
        // 카탈로그/회원 이름을 nanoTime 으로 유니크화해 UNIQUE 충돌을 회피한다.
        String tag = String.valueOf(System.nanoTime());
        return txTemplate.execute(s -> {
            Artist artist = artistRepository.save(Artist.create("Artist-" + tag, null));
            Genre genre = genreRepository.save(Genre.create("Genre-" + tag));
            Label label = labelRepository.save(Label.create("Label-" + tag));
            Album album = albumRepository.save(Album.create("Title-" + tag, artist, genre, label,
                    (short) 2020, AlbumFormat.LP_12, unitPrice, stock,
                    AlbumStatus.SELLING, false, null, null));
            Member member = memberRepository.save(MemberFixtures.register(
                    "c91-" + tag + "@example.com",
                    "encoded", "Coupon Tester", "010-1234-5678"));

            Coupon coupon = Coupon.builder("쿠폰-" + tag, type, discountValue,
                            Instant.now().minus(1, ChronoUnit.DAYS),
                            Instant.now().plus(10, ChronoUnit.DAYS))
                    .minOrderAmount(minOrder)
                    .maxDiscountAmount(maxDiscount)
                    .build();
            coupon = couponRepository.save(coupon);
            MemberCoupon memberCoupon = memberCouponRepository.save(MemberCoupon.issue(coupon, member.getId(), Instant.now()));
            return new Fixtures(member, album, coupon, memberCoupon);
        });
    }

    private OrderCreateRequest orderRequest(Long albumId, int qty, Long memberCouponId) {
        return new OrderCreateRequest(
                List.of(new OrderItemRequest(albumId, qty)),
                null,
                OrderFixtures.sampleShippingInfoRequest(),
                memberCouponId);
    }

    @Test
    @DisplayName("쿠폰 적용 회원 주문 → discount 반영 + 결제 amount == payable + 회원쿠폰 USED")
    void couponApplied_orderAndPayment() {
        Fixtures f = setupFixtures(30_000L, 10, CouponDiscountType.FIXED_AMOUNT, 5_000L, 0L, null);

        Order order = txTemplate.execute(s ->
                orderService.place(f.member.getId(),
                        orderRequest(f.album.getId(), 2, f.memberCoupon.getId())));

        assertThat(order.getTotalAmount()).isEqualTo(60_000L);
        assertThat(order.getDiscountAmount()).isEqualTo(5_000L);
        assertThat(order.getPayableAmount()).isEqualTo(55_000L);

        // 회원쿠폰이 USED 로 전이됐고 order_id 가 연결됐는지 다른 트랜잭션에서 다시 읽어 확인.
        MemberCoupon afterApply = memberCouponRepository.findById(f.memberCoupon.getId()).orElseThrow();
        assertThat(afterApply.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThat(afterApply.getOrderId()).isEqualTo(order.getId());

        // 결제 요청 — Payment.amount 와 PG 청구액 모두 payable.
        PaymentApiResponse payment = txTemplate.execute(s ->
                paymentService.requestPayment(f.member.getId(),
                        new PaymentCreateRequest(order.getOrderNumber(), PaymentMethod.CARD)));

        assertThat(payment.amount()).isEqualTo(55_000L);
    }

    @Test
    @DisplayName("쿠폰 적용 주문 취소 (PENDING) → 쿠폰 ISSUED 복원 + 재고 복원 (#91 DoD 양 경로 중 1)")
    void cancelPendingOrder_restoresCoupon() {
        Fixtures f = setupFixtures(30_000L, 10, CouponDiscountType.FIXED_AMOUNT, 5_000L, 0L, null);

        Order order = txTemplate.execute(s ->
                orderService.place(f.member.getId(),
                        orderRequest(f.album.getId(), 2, f.memberCoupon.getId())));

        txTemplate.executeWithoutResult(s ->
                orderService.cancel(f.member.getId(), order.getOrderNumber(), "변심"));

        MemberCoupon restored = memberCouponRepository.findById(f.memberCoupon.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(restored.getOrderId()).isNull();
        assertThat(restored.getUsedAt()).isNull();

        Album restoredAlbum = albumRepository.findById(f.album.getId()).orElseThrow();
        assertThat(restoredAlbum.getStock()).isEqualTo(10);

        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("쿠폰 적용 주문 환불 (PAID→REFUNDED) → 쿠폰 ISSUED 복원 (#91 DoD 양 경로 중 2)")
    void refundPaidOrder_restoresCoupon() {
        Fixtures f = setupFixtures(30_000L, 10, CouponDiscountType.FIXED_AMOUNT, 5_000L, 0L, null);

        Order order = txTemplate.execute(s ->
                orderService.place(f.member.getId(),
                        orderRequest(f.album.getId(), 2, f.memberCoupon.getId())));

        // PaymentService 가 PENDING Payment 를 만든 뒤, 별도 트랜잭션에서 PAID 로 전이시켜 환불 가능 상태로 만든다.
        txTemplate.executeWithoutResult(s ->
                paymentService.requestPayment(f.member.getId(),
                        new PaymentCreateRequest(order.getOrderNumber(), PaymentMethod.CARD)));
        txTemplate.executeWithoutResult(s -> {
            Order managed = orderRepository.findById(order.getId()).orElseThrow();
            Payment payment = paymentRepository.findByOrderId(managed.getId()).orElseThrow();
            payment.markPaid(Instant.now());
            managed.changeStatus(OrderStatus.PAID, null, Instant.now());
        });

        RefundResult result = txTemplate.execute(s ->
                adminOrderService.refund(order.getOrderNumber(), "운영 환불"));

        assertThat(result.alreadyRefunded()).isFalse();

        MemberCoupon restored = memberCouponRepository.findById(f.memberCoupon.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
        assertThat(restored.getOrderId()).isNull();

        Album restoredAlbum = albumRepository.findById(f.album.getId()).orElseThrow();
        assertThat(restoredAlbum.getStock()).isEqualTo(10);

        // 결제 금액 == payable (쿠폰 할인 반영된 금액) 이 환불 후에도 보존됨. 60000 - 5000 = 55000.
        Payment refunded = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(refunded.getAmount()).isEqualTo(55_000L);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("게스트 + memberCouponId → CouponNotApplicableException, 주문/재고 미생성")
    void guestWithCoupon_rejected() {
        Fixtures f = setupFixtures(30_000L, 10, CouponDiscountType.FIXED_AMOUNT, 5_000L, 0L, null);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(s ->
                orderService.place(null, new OrderCreateRequest(
                        List.of(new OrderItemRequest(f.album.getId(), 2)),
                        new GuestInfoRequest("guest@example.com", "010-1111-2222"),
                        OrderFixtures.sampleShippingInfoRequest(),
                        f.memberCoupon.getId()))))
                .isInstanceOf(CouponNotApplicableException.class);

        // 게스트+쿠폰 검증이 stock 차감 이전에 일어나므로 재고가 차감되지 않았어야 한다.
        Album album = albumRepository.findById(f.album.getId()).orElseThrow();
        assertThat(album.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("정률(PERCENTAGE) 쿠폰 + maxDiscount 캡 → 실 계산으로 캡 적용된 할인 반영, payable·USED 검증")
    void percentageCoupon_appliedWithMaxDiscountCap() {
        // 정률 10%면 6,000 이지만 maxDiscount 로 4,000 에 걸린다.
        Fixtures f = setupFixtures(30_000L, 10, CouponDiscountType.PERCENTAGE, 10L, 0L, 4_000L);

        Order order = txTemplate.execute(s ->
                orderService.place(f.member.getId(),
                        orderRequest(f.album.getId(), 2, f.memberCoupon.getId())));

        assertThat(order.getTotalAmount()).isEqualTo(60_000L);
        assertThat(order.getDiscountAmount()).isEqualTo(4_000L);
        assertThat(order.getPayableAmount()).isEqualTo(56_000L);

        MemberCoupon afterApply = memberCouponRepository.findById(f.memberCoupon.getId()).orElseThrow();
        assertThat(afterApply.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThat(afterApply.getOrderId()).isEqualTo(order.getId());

        PaymentApiResponse payment = txTemplate.execute(s ->
                paymentService.requestPayment(f.member.getId(),
                        new PaymentCreateRequest(order.getOrderNumber(), PaymentMethod.CARD)));
        assertThat(payment.amount()).isEqualTo(56_000L);
    }

    @Test
    @DisplayName("최소주문금액 미달 쿠폰 → CouponMinOrderNotMetException, 주문 미생성·재고 미차감(트랜잭션 롤백)")
    void minOrderNotMet_rejectsPlace() {
        // minOrder 가 총액보다 커서 적용 단계(save 이후)에서 예외 → 트랜잭션 전체 롤백.
        Fixtures f = setupFixtures(30_000L, 10, CouponDiscountType.FIXED_AMOUNT, 5_000L, 100_000L, null);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(s ->
                orderService.place(f.member.getId(),
                        orderRequest(f.album.getId(), 2, f.memberCoupon.getId()))))
                .isInstanceOf(CouponMinOrderNotMetException.class);

        // 공유 DB 라 전역 조회 대신 이 회원 주문으로 좁혀 단언한다.
        assertThat(orderRepository.findByMemberId(f.member.getId(), Pageable.unpaged())).isEmpty();
        Album album = albumRepository.findById(f.album.getId()).orElseThrow();
        assertThat(album.getStock()).isEqualTo(10);

        MemberCoupon coupon = memberCouponRepository.findById(f.memberCoupon.getId()).orElseThrow();
        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.ISSUED);
    }
}
