package com.groove.claim.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimItem;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.domain.ClaimType;
import com.groove.claim.exception.ClaimItemNotInOrderException;
import com.groove.claim.exception.ExcessiveReturnQuantityException;
import com.groove.claim.exception.OrderNotCancellableException;
import com.groove.claim.exception.OrderNotReturnableException;
import com.groove.claim.exception.ReturnWindowExpiredException;
import com.groove.claim.exception.ReturnWindowNotDeterminableException;
import com.groove.coupon.application.CouponApplicationService;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentNotRefundableException;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.shipping.application.ShippingService;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimService 단위 테스트")
class ClaimServiceTest {

    private static final String ORDER_NUMBER = "ORD-20260612-A1B2C3";
    private static final long MEMBER_ID = 1L;
    private static final long ORDER_ID = 100L;
    private static final long ITEM_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration WINDOW = Duration.ofDays(7);

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private CouponApplicationService couponApplicationService;
    @Mock
    private ShippingService shippingService;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private MemberRepository memberRepository;

    private ClaimService claimService;

    private static final long ALBUM_ID = 50L;

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(claimRepository, orderRepository, paymentRepository, paymentGateway,
                couponApplicationService, shippingService, albumRepository, memberRepository, CLOCK, WINDOW);
        // request 경로는 본인 주문 검증 직후 회원 활성(#269)을 확인한다 — 기본은 활성 회원으로 둔다.
        lenient().when(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).thenReturn(true);
    }

    // --- fixtures -----------------------------------------------------------

    private Album album(long price) {
        Album album = Album.create("Album", Artist.create("A", null), Genre.create("Rock"), Label.create("L"),
                (short) 2020, AlbumFormat.LP_12, price, 100, AlbumStatus.SELLING, false, null, null);
        ReflectionTestUtils.setField(album, "id", ALBUM_ID);
        return album;
    }

    /** unitPrice×qty 한 항목 + 선택 할인 + DELIVERED 상태로 세팅한 주문(주문/항목 id 주입). */
    private Order deliveredOrder(long unitPrice, int qty, long discount) {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, MEMBER_ID);
        order.addItem(OrderItem.create(album(unitPrice), qty));
        if (discount > 0) {
            order.applyDiscount(discount); // 상태 세팅 전에 호출
        }
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);
        ReflectionTestUtils.setField(order.getItems().get(0), "id", ITEM_ID);
        return order;
    }

    private Payment paidPayment(Order order) {
        Payment payment = Payment.initiate(order, order.getPayableAmount(), PaymentMethod.CARD, "MOCK", "mock-tx-1");
        payment.markPaid(CLOCK.instant());
        ReflectionTestUtils.setField(payment, "id", 500L);
        return payment;
    }

    private Claim inspectingClaim(Order order, int returnQty) {
        Claim claim = Claim.request(order, "단순 변심");
        claim.addItem(ClaimItem.of(order.getItems().get(0), returnQty));
        claim.approve(CLOCK.instant());
        claim.startTransit(CLOCK.instant());
        claim.startInspecting(CLOCK.instant());
        ReflectionTestUtils.setField(claim, "id", 7L);
        return claim;
    }

    private ClaimCreateCommand command(int quantity) {
        return new ClaimCreateCommand(MEMBER_ID, ORDER_NUMBER, "단순 변심",
                List.of(new ClaimCreateCommand.Line(ITEM_ID, quantity)));
    }

    /** unitPrice×qty 한 항목 + 선택 할인 + PAID 상태(발송 전)로 세팅한 주문. */
    private Order paidOrder(long unitPrice, int qty, long discount) {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, MEMBER_ID);
        order.addItem(OrderItem.create(album(unitPrice), qty));
        if (discount > 0) {
            order.applyDiscount(discount); // 상태 세팅 전에 호출
        }
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
        ReflectionTestUtils.setField(order.getItems().get(0), "id", ITEM_ID);
        return order;
    }

    private OrderPartialCancelCommand cancelCommand(int quantity) {
        return new OrderPartialCancelCommand(ORDER_NUMBER, "관리자 부분취소",
                List.of(new ClaimCreateCommand.Line(ITEM_ID, quantity)));
    }

    // --- request ------------------------------------------------------------

    @Test
    @DisplayName("request: 정상 — 잔여 수량 이내 부분 반품을 접수한다")
    void request_happyPath() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.of(CLOCK.instant().minus(Duration.ofDays(1))));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());
        given(claimRepository.save(any(Claim.class))).willAnswer(inv -> inv.getArgument(0));

        Claim claim = claimService.request(command(1));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(claim.getItems()).hasSize(1);
        assertThat(claim.getItems().get(0).getQuantity()).isEqualTo(1);
        verify(claimRepository).save(any(Claim.class));
    }

    @Test
    @DisplayName("request: 배송행 없어도 order.deliveredAt 으로 기한 결정 — 관리자 강제 전이/시드 주문 반품 가능")
    void request_anchorsOnOrderDeliveredAtWithoutShippingRow() {
        Order order = deliveredOrder(15_000L, 2, 0);
        // 배송 행 없이 DELIVERED 로 전이된 주문 — order.deliveredAt 이 결정적 anchor 가 된다.
        ReflectionTestUtils.setField(order, "deliveredAt", CLOCK.instant().minus(Duration.ofDays(1)));
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());
        given(claimRepository.save(any(Claim.class))).willAnswer(inv -> inv.getArgument(0));

        Claim claim = claimService.request(command(1));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        // order.deliveredAt 이 있으면 shipping 애그리거트는 조회하지 않는다.
        verify(shippingService, never()).findDeliveredAt(any());
    }

    @Test
    @DisplayName("request: 배송행 deliveredAt 부재 시 order.updated_at 폴백은 제거됨 — 최근 updated_at 이라도 결정 불가로 거부")
    void request_ignoresOrderUpdatedAtWhenShippingMissing() {
        Order order = deliveredOrder(15_000L, 2, 0);
        // updated_at 이 window 내(1일 전)라도 비결정적이므로 anchor 로 쓰지 않는다.
        ReflectionTestUtils.setField(order, "updatedAt", CLOCK.instant().minus(Duration.ofDays(1)));
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.request(command(1)))
                .isInstanceOf(ReturnWindowNotDeterminableException.class);
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("request: 본인 주문이 아니면(memberId 불일치) OrderNotFoundException")
    void request_rejectsNonOwner() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));

        ClaimCreateCommand cmd = new ClaimCreateCommand(999L, ORDER_NUMBER, "변심",
                List.of(new ClaimCreateCommand.Line(ITEM_ID, 1)));
        assertThatThrownBy(() -> claimService.request(cmd)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("request: 탈퇴(soft delete) 회원이 만료 전 토큰으로 접수하면 MemberNotFoundException, 저장 안 함 (#269)")
    void request_rejectsWithdrawnMember() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(memberRepository.existsByIdAndDeletedAtIsNull(MEMBER_ID)).willReturn(false);

        assertThatThrownBy(() -> claimService.request(command(1)))
                .isInstanceOf(MemberNotFoundException.class);
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("request: 배송완료 전 주문이면 OrderNotReturnableException")
    void request_rejectsNonReturnableStatus() {
        Order order = deliveredOrder(15_000L, 2, 0);
        ReflectionTestUtils.setField(order, "status", OrderStatus.PREPARING);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> claimService.request(command(1)))
                .isInstanceOf(OrderNotReturnableException.class);
    }

    @Test
    @DisplayName("request: 배송완료 시각이 없으면 ReturnWindowNotDeterminableException")
    void request_rejectsWindowNotDeterminable() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.request(command(1)))
                .isInstanceOf(ReturnWindowNotDeterminableException.class);
    }

    @Test
    @DisplayName("request: 반품 기한(window) 초과면 ReturnWindowExpiredException")
    void request_rejectsWindowExpired() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.of(CLOCK.instant().minus(Duration.ofDays(10))));

        assertThatThrownBy(() -> claimService.request(command(1)))
                .isInstanceOf(ReturnWindowExpiredException.class);
    }

    @Test
    @DisplayName("request: 주문에 없는 항목이면 ClaimItemNotInOrderException")
    void request_rejectsItemNotInOrder() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.of(CLOCK.instant().minus(Duration.ofDays(1))));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());

        ClaimCreateCommand cmd = new ClaimCreateCommand(MEMBER_ID, ORDER_NUMBER, "변심",
                List.of(new ClaimCreateCommand.Line(99_999L, 1)));
        assertThatThrownBy(() -> claimService.request(cmd)).isInstanceOf(ClaimItemNotInOrderException.class);
    }

    @Test
    @DisplayName("request: 잔여 수량을 초과하면 ExcessiveReturnQuantityException (이미 반품된 수량 차감)")
    void request_rejectsExcessiveQuantity() {
        Order order = deliveredOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.of(CLOCK.instant().minus(Duration.ofDays(1))));
        // 이미 2개 반품된(비-REJECTED) 기존 claim → 잔여 0
        Claim prior = Claim.request(order, "이전");
        prior.addItem(ClaimItem.of(order.getItems().get(0), 2));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of(prior));

        assertThatThrownBy(() -> claimService.request(command(1)))
                .isInstanceOf(ExcessiveReturnQuantityException.class);
    }

    // --- completeRefund -----------------------------------------------------

    @Test
    @DisplayName("completeRefund: 부분 반품 — 결제 PARTIALLY_REFUNDED, 해당 항목 재입고, 쿠폰 미복원, 주문 미반품")
    void completeRefund_partial() {
        Order order = deliveredOrder(15_000L, 2, 0); // total/payable = 30000
        Claim claim = inspectingClaim(order, 1); // 1/2 반품 → gross 15000
        Payment payment = paidPayment(order);
        given(claimRepository.findByIdForUpdate(7L)).willReturn(Optional.of(claim));
        given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));
        // 무쿠폰 — 반품 품목 정가만 환불.
        given(couponApplicationService.appliedCouponMinOrderAmount(ORDER_ID)).willReturn(OptionalLong.empty());

        Claim result = claimService.completeRefund(7L);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualTo(15_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);
        verify(albumRepository).restoreStock(ALBUM_ID, 1); // 검수 통과 1개 재입고
        assertThat(order.getReturnedAt()).isNull();
        verify(paymentGateway).refund(any());
        verify(couponApplicationService, never()).restoreForOrder(any());
    }

    @Test
    @DisplayName("completeRefund: 전량 반품 — 결제 REFUNDED, 쿠폰 복원, 주문 반품 마커, 할인 반영 환불액(payable)")
    void completeRefund_full_withDiscount() {
        Order order = deliveredOrder(15_000L, 2, 6_000L); // total 30000, discount 6000, payable 24000
        Claim claim = inspectingClaim(order, 2); // 2/2 전량 → gross 30000 == total
        Payment payment = paidPayment(order); // amount = payable = 24000
        given(claimRepository.findByIdForUpdate(7L)).willReturn(Optional.of(claim));
        given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));
        // 쿠폰 적용(할인 6000) — 비례 배분 경로.
        given(couponApplicationService.appliedCouponMinOrderAmount(ORDER_ID)).willReturn(OptionalLong.of(20_000L));
        given(claimRepository.findByOrder_IdAndStatus(ORDER_ID, ClaimStatus.REFUNDED)).willReturn(List.of());

        Claim result = claimService.completeRefund(7L);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualTo(24_000L); // 할인 반영 — total 이 아닌 payable
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(24_000L);
        assertThat(order.getReturnedAt()).isNotNull();
        verify(albumRepository).restoreStock(ALBUM_ID, 2); // 전량 재입고
        verify(couponApplicationService).restoreForOrder(ORDER_ID);
    }

    @Test
    @DisplayName("completeRefund: INSPECTING 이 아니면 부수효과 없이 멱등 no-op")
    void completeRefund_idempotentWhenNotInspecting() {
        Order order = deliveredOrder(15_000L, 2, 0);
        Claim claim = Claim.request(order, "변심"); // REQUESTED
        ReflectionTestUtils.setField(claim, "id", 7L);
        given(claimRepository.findByIdForUpdate(7L)).willReturn(Optional.of(claim));

        Claim result = claimService.completeRefund(7L);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        verifyNoInteractions(paymentRepository, paymentGateway, couponApplicationService, albumRepository);
    }

    @Test
    @DisplayName("completeRefund: 결제가 PAID/PARTIALLY_REFUNDED 가 아니면 PaymentNotRefundableException")
    void completeRefund_rejectsNonRefundablePayment() {
        Order order = deliveredOrder(15_000L, 2, 0);
        Claim claim = inspectingClaim(order, 1);
        Payment failed = Payment.initiate(order, order.getPayableAmount(), PaymentMethod.CARD, "MOCK", "tx-f");
        failed.markFailed("x");
        ReflectionTestUtils.setField(failed, "id", 500L);
        given(claimRepository.findByIdForUpdate(7L)).willReturn(Optional.of(claim));
        given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(failed));

        assertThatThrownBy(() -> claimService.completeRefund(7L))
                .isInstanceOf(PaymentNotRefundableException.class);
        verify(paymentGateway, never()).refund(any());
        verifyNoInteractions(albumRepository);
    }

    // --- proportionalRefund (비례 배분 단위) ---------------------------------

    @Test
    @DisplayName("proportionalRefund: 할인 없는 부분 반품은 정가 비례")
    void proportionalRefund_partialNoDiscount() {
        assertThat(ClaimService.proportionalRefund(30_000L, 30_000L, 15_000L, 0L)).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("proportionalRefund: 할인 주문은 payable 비례 + 전량 시 누적 정산")
    void proportionalRefund_withDiscountCumulative() {
        // payable 24000, totalGross 30000 — 1차(절반): 12000, 2차(전량): payable - 기환불 = 12000
        assertThat(ClaimService.proportionalRefund(24_000L, 30_000L, 15_000L, 0L)).isEqualTo(12_000L);
        assertThat(ClaimService.proportionalRefund(24_000L, 30_000L, 30_000L, 12_000L)).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("proportionalRefund: payable×cumGross 오버플로를 BigInteger 로 방지")
    void proportionalRefund_noOverflow() {
        long big = 10_000_000_000L; // 1e10 — long*long 시 1e20 으로 오버플로
        assertThat(ClaimService.proportionalRefund(big, big, big, 0L)).isEqualTo(big);
    }

    @Test
    @DisplayName("proportionalRefund: 반올림으로 증분이 0 이하면 1원 전진(잔여 한도 내)")
    void proportionalRefund_zeroIncrementFloor() {
        assertThat(ClaimService.proportionalRefund(100L, 100L, 0L, 0L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("proportionalRefund: 환불 가능액 소진(잔여 0)이면 0 (호출 측이 PG/누적 갱신을 건너뜀)")
    void proportionalRefund_exhaustedReturnsZero() {
        assertThat(ClaimService.proportionalRefund(1L, 100L, 200L, 1L)).isZero();
    }

    // --- cancelPartially (발송 전 부분 취소) ----------------------------

    private void stubCancelLookups(Order order, Payment payment, OptionalLong couponMinOrder) {
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());
        given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));
        given(claimRepository.save(any(Claim.class))).willAnswer(inv -> {
            Claim saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L); // DB IDENTITY 부여 모사
            return saved;
        });
        given(couponApplicationService.appliedCouponMinOrderAmount(ORDER_ID)).willReturn(couponMinOrder);
        // 누적 REFUNDED 정가 조회는 쿠폰 적용 시에만 발생 — 쿠폰 present 테스트에서만 스텁한다.
        if (couponMinOrder.isPresent()) {
            given(claimRepository.findByOrder_IdAndStatus(ORDER_ID, ClaimStatus.REFUNDED)).willReturn(List.of());
        }
    }

    @Test
    @DisplayName("cancelPartially: 무쿠폰 부분취소 — 정가 비례 환불, 해당 수량 재입고, 결제 PARTIALLY_REFUNDED, 주문 PAID 유지")
    void cancelPartially_noCoupon_partial() {
        Order order = paidOrder(15_000L, 2, 0); // total/payable 30000
        Payment payment = paidPayment(order);
        stubCancelLookups(order, payment, OptionalLong.empty());

        Claim result = claimService.cancelPartially(cancelCommand(1));

        assertThat(result.getClaimType()).isEqualTo(ClaimType.CANCEL);
        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualTo(15_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(albumRepository).restoreStock(ALBUM_ID, 1);
        verify(paymentGateway).refund(any());
        verify(couponApplicationService, never()).restoreForOrder(any());
        verify(shippingService, never()).cancelForOrder(any());
    }

    @Test
    @DisplayName("cancelPartially: 쿠폰 유효(잔여≥최소주문금액) — 할인 안분 환불, 쿠폰 USED 유지")
    void cancelPartially_couponValid_proratedDiscount() {
        Order order = paidOrder(15_000L, 2, 6_000L); // total 30000, discount 6000, payable 24000
        Payment payment = paidPayment(order); // amount 24000
        stubCancelLookups(order, payment, OptionalLong.of(10_000L)); // 잔여 15000 ≥ 10000

        Claim result = claimService.cancelPartially(cancelCommand(1));

        assertThat(result.getRefundAmount()).isEqualTo(12_000L); // 24000 × 15000/30000
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(12_000L);
        verify(couponApplicationService, never()).restoreForOrder(any());
    }

    @Test
    @DisplayName("cancelPartially: 쿠폰 무효(잔여<최소주문금액) — 할인분 차감 환불(C̄−D), 쿠폰 복원")
    void cancelPartially_couponVoided_belowMinOrder() {
        Order order = paidOrder(15_000L, 2, 6_000L); // total 30000, discount 6000, payable 24000
        Payment payment = paidPayment(order);
        stubCancelLookups(order, payment, OptionalLong.of(20_000L)); // 잔여 15000 < 20000 → 무효

        Claim result = claimService.cancelPartially(cancelCommand(1));

        assertThat(result.getRefundAmount()).isEqualTo(9_000L); // 취소정가 15000 − 적용할인 6000
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(9_000L);
        verify(couponApplicationService).restoreForOrder(ORDER_ID); // 쿠폰 무효 → 복원
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("cancelPartially: 전량 취소 — 결제 REFUNDED, 주문 CANCELLED, 발송 전 배송 취소, 쿠폰 복원")
    void cancelPartially_full() {
        Order order = paidOrder(15_000L, 2, 0); // total/payable 30000
        Payment payment = paidPayment(order);
        stubCancelLookups(order, payment, OptionalLong.empty());

        Claim result = claimService.cancelPartially(cancelCommand(2)); // 전량

        assertThat(result.getRefundAmount()).isEqualTo(30_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(albumRepository).restoreStock(ALBUM_ID, 2);
        verify(shippingService).cancelForOrder(ORDER_ID);
        verify(couponApplicationService).restoreForOrder(ORDER_ID);
    }

    @Test
    @DisplayName("cancelPartially: 취소가능 수량 초과면 ExcessiveReturnQuantityException (결제 락 전에 실패)")
    void cancelPartially_rejectsExcessiveQuantity() {
        Order order = paidOrder(15_000L, 2, 0);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());

        assertThatThrownBy(() -> claimService.cancelPartially(cancelCommand(3)))
                .isInstanceOf(ExcessiveReturnQuantityException.class);
        verifyNoInteractions(paymentRepository, paymentGateway);
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelPartially: 발송 전 상태(PAID/PREPARING)가 아니면 OrderNotCancellableException")
    void cancelPartially_rejectsNonPreShipStatus() {
        Order order = paidOrder(15_000L, 2, 0);
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED); // 발송완료 → 반품 경로
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> claimService.cancelPartially(cancelCommand(1)))
                .isInstanceOf(OrderNotCancellableException.class);
        verifyNoInteractions(paymentRepository, paymentGateway, albumRepository);
    }

    @Test
    @DisplayName("cancelPartially: 결제가 PAID/PARTIALLY_REFUNDED 가 아니면 PaymentNotRefundableException")
    void cancelPartially_rejectsNonRefundablePayment() {
        Order order = paidOrder(15_000L, 2, 0);
        Payment failed = Payment.initiate(order, order.getPayableAmount(), PaymentMethod.CARD, "MOCK", "tx-f");
        failed.markFailed("x");
        ReflectionTestUtils.setField(failed, "id", 500L);
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());
        given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(failed));

        assertThatThrownBy(() -> claimService.cancelPartially(cancelCommand(1)))
                .isInstanceOf(PaymentNotRefundableException.class);
        verify(paymentGateway, never()).refund(any());
        verify(claimRepository, never()).save(any());
    }

    // --- refundIncrement (쿠폰 인지 환불 계산, 취소/반품 공용) -------

    @Test
    @DisplayName("refundIncrement: 쿠폰 미적용은 이번 클레임 정가만 환불(voidCoupon=false)")
    void refundIncrement_noCoupon() {
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                30_000L, 30_000L, 15_000L, 15_000L, 0L, OptionalLong.empty(), true);
        assertThat(r.amount()).isEqualTo(15_000L);
        assertThat(r.voidCoupon()).isFalse();
    }

    @Test
    @DisplayName("refundIncrement: 쿠폰 적용 + 잔여≥최소주문금액이면 안분(voidCoupon=false)")
    void refundIncrement_couponValid() {
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                24_000L, 30_000L, 15_000L, 15_000L, 0L, OptionalLong.of(10_000L), true);
        assertThat(r.amount()).isEqualTo(12_000L);
        assertThat(r.voidCoupon()).isFalse();
    }

    @Test
    @DisplayName("refundIncrement: 취소 + 잔여<최소주문금액이면 무효(C̄−D, voidCoupon=true)")
    void refundIncrement_couponVoided() {
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                24_000L, 30_000L, 15_000L, 15_000L, 0L, OptionalLong.of(20_000L), true);
        assertThat(r.amount()).isEqualTo(9_000L); // 15000 − 6000
        assertThat(r.voidCoupon()).isTrue();
    }

    @Test
    @DisplayName("refundIncrement: 전량 무효는 payable 전액 환불")
    void refundIncrement_voidedFull() {
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                24_000L, 30_000L, 30_000L, 30_000L, 0L, OptionalLong.of(20_000L), true);
        assertThat(r.amount()).isEqualTo(24_000L); // 30000 − 6000
        assertThat(r.voidCoupon()).isTrue();
    }

    @Test
    @DisplayName("refundIncrement: 적용 할인 > 취소 정가(클로백 필요)면 무효화 않고 비례 폴백")
    void refundIncrement_clawbackFallsBackToProportional() {
        // total 30000, payable 24000(할인 6000), 취소 정가 5000, 잔여 25000 < 최소 27000 → 무효 진입,
        // voidTarget = 5000 − 6000 = −1000 < 0 → 비례 폴백.
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                24_000L, 30_000L, 5_000L, 5_000L, 0L, OptionalLong.of(27_000L), true);
        assertThat(r.amount()).isEqualTo(4_000L); // 24000 × 5000/30000
        assertThat(r.voidCoupon()).isFalse();
    }

    @Test
    @DisplayName("refundIncrement: 무효화 후 후속 환불은 정가 기준(누적 비례 곡선 이탈 불일치 회피, #238 리뷰)")
    void refundIncrement_afterVoid_usesFullGross() {
        // couponMinOrder empty(쿠폰 무효·복원 후) — 이번 클레임 정가(10000)만 환불. 기환불 4000 → 잔여 20000 한도 내.
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                24_000L, 30_000L, 10_000L, 20_000L, 4_000L, OptionalLong.empty(), true);
        assertThat(r.amount()).isEqualTo(10_000L);
        assertThat(r.voidCoupon()).isFalse();
    }

    @Test
    @DisplayName("refundIncrement: 반품(allowCouponVoid=false)은 잔여<최소주문금액이어도 무효화하지 않고 안분")
    void refundIncrement_returnNeverVoids() {
        ClaimService.RefundComputation r = ClaimService.refundIncrement(
                24_000L, 30_000L, 15_000L, 15_000L, 0L, OptionalLong.of(20_000L), false);
        assertThat(r.amount()).isEqualTo(12_000L); // 안분 — 무효 분기 미진입
        assertThat(r.voidCoupon()).isFalse();
    }
}
