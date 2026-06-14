package com.groove.claim.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimItem;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.claim.exception.ClaimItemNotInOrderException;
import com.groove.claim.exception.ExcessiveReturnQuantityException;
import com.groove.claim.exception.OrderNotReturnableException;
import com.groove.claim.exception.ReturnWindowExpiredException;
import com.groove.claim.exception.ReturnWindowNotDeterminableException;
import com.groove.coupon.application.CouponApplicationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    private ClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(claimRepository, orderRepository, paymentRepository, paymentGateway,
                couponApplicationService, shippingService, CLOCK, WINDOW);
    }

    // --- fixtures -----------------------------------------------------------

    private Album album(long price) {
        return Album.create("Album", Artist.create("A", null), Genre.create("Rock"), Label.create("L"),
                (short) 2020, AlbumFormat.LP_12, price, 100, AlbumStatus.SELLING, false, null, null);
    }

    /** unitPrice×qty 한 항목 + 선택 할인 + DELIVERED 상태로 세팅한 주문(주문/항목 id 주입). */
    private Order deliveredOrder(long unitPrice, int qty, long discount) {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, MEMBER_ID);
        order.addItem(OrderItem.create(album(unitPrice), qty));
        if (discount > 0) {
            order.applyDiscount(discount); // PENDING 상태에서만 가능 — 상태 세팅 전에 호출
        }
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);
        ReflectionTestUtils.setField(order.getItems().get(0), "id", ITEM_ID);
        return order;
    }

    private Payment paidPayment(Order order) {
        Payment payment = Payment.initiate(order, order.getPayableAmount(), PaymentMethod.CARD, "MOCK", "mock-tx-1");
        payment.markPaid();
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
    @DisplayName("request: 배송행 deliveredAt 부재 시 주문 updated_at 으로 기한 폴백(관리자 강제 DELIVERED)")
    void request_fallsBackToOrderUpdatedAtWhenShippingMissing() {
        Order order = deliveredOrder(15_000L, 2, 0);
        ReflectionTestUtils.setField(order, "updatedAt", CLOCK.instant().minus(Duration.ofDays(1)));
        given(orderRepository.findByOrderNumberForUpdate(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(shippingService.findDeliveredAt(ORDER_ID)).willReturn(Optional.empty());
        given(claimRepository.findByOrder_IdAndStatusNot(ORDER_ID, ClaimStatus.REJECTED)).willReturn(List.of());
        given(claimRepository.save(any(Claim.class))).willAnswer(inv -> inv.getArgument(0));

        Claim claim = claimService.request(command(1));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
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
        int stockBefore = order.getItems().get(0).getAlbum().getStock();
        Payment payment = paidPayment(order);
        given(claimRepository.findByIdForUpdate(7L)).willReturn(Optional.of(claim));
        given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));
        given(claimRepository.findByOrder_IdAndStatus(ORDER_ID, ClaimStatus.REFUNDED)).willReturn(List.of());

        Claim result = claimService.completeRefund(7L);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualTo(15_000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(15_000L);
        assertThat(order.getItems().get(0).getAlbum().getStock()).isEqualTo(stockBefore + 1);
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
        given(claimRepository.findByOrder_IdAndStatus(ORDER_ID, ClaimStatus.REFUNDED)).willReturn(List.of());

        Claim result = claimService.completeRefund(7L);

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REFUNDED);
        assertThat(result.getRefundAmount()).isEqualTo(24_000L); // 할인 반영 — total 이 아닌 payable
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(24_000L);
        assertThat(order.getReturnedAt()).isNotNull();
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
        verifyNoInteractions(paymentRepository, paymentGateway, couponApplicationService);
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
}
