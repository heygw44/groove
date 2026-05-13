package com.groove.admin.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.exception.PaymentNotRefundableException;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderService — 관리자 주문 조회 / 상태 강제 전환 / 환불")
class AdminOrderServiceTest {

    private static final String ORDER_NUMBER = "ORD-20260513-A1B2C3";
    private static final long UNIT_PRICE = 30_000L;
    private static final int QTY = 2;
    private static final long ORDER_AMOUNT = UNIT_PRICE * QTY;
    private static final String PG_TX = "mock-tx-1";
    private static final long PAYMENT_ID = 100L;
    /** {@link Payment#refundIdempotencyKey()} 결정성에 의존하는 검증용 — 같은 paymentId/PG_TX 면 항상 동일. */
    private static final String EXPECTED_IDEM_KEY = "refund:" + PAYMENT_ID + ":" + PG_TX;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;

    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository, paymentRepository, paymentGateway);
    }

    private Album album(int initialStock) {
        return Album.create("Album", Artist.create("Artist", null), Genre.create("Rock"), Label.create("Label"),
                (short) 2020, AlbumFormat.LP_12, UNIT_PRICE, initialStock, AlbumStatus.SELLING, false, null, null);
    }

    /** orderNumber/배송지를 끼운 PENDING 회원 주문 + 라인 1개(qty=2). place() 의 재고 차감은 흉내 내지 않는다. */
    private Order pendingOrder(Album album) {
        Order order = OrderFixtures.memberOrder(ORDER_NUMBER, 1L);
        order.addItem(OrderItem.create(album, QTY));
        return order;
    }

    /** PENDING → ... → target 합법 경로를 따라 전이된 주문. */
    private Order orderAt(OrderStatus target, Album album) {
        Order order = pendingOrder(album);
        List<OrderStatus> path = switch (target) {
            case PENDING -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case PREPARING -> List.of(OrderStatus.PAID, OrderStatus.PREPARING);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            default -> throw new IllegalArgumentException("지원하지 않는 target: " + target);
        };
        path.forEach(s -> order.changeStatus(s, null));
        return order;
    }

    private Payment paidPaymentFor(Order order) {
        Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", PG_TX);
        payment.markPaid();
        // refundIdempotencyKey() 는 영속화된 id 를 요구하므로 단위 테스트에서 강제 주입한다 (#72).
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        return payment;
    }

    @Nested
    @DisplayName("list / findDetail")
    class Read {

        @Test
        @DisplayName("findDetail: 미존재 주문 → OrderNotFoundException")
        void findDetail_notFound() {
            when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findDetail(ORDER_NUMBER))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("list: 필터 조합으로 Specification 을 만들어 페이징 조회한다")
        void list_delegatesToRepository() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> expected = new PageImpl<>(List.of());
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

            Page<Order> result = service.list(
                    new AdminOrderSearchCriteria(OrderStatus.PAID, 1L, Instant.parse("2026-05-01T00:00:00Z"), null),
                    pageable);

            assertThat(result).isSameAs(expected);
            verify(orderRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("changeStatus")
    class ChangeStatus {

        @Test
        @DisplayName("합법 전진 전이(PAID→PREPARING) → 상태 갱신")
        void legalForwardTransition() {
            Order order = orderAt(OrderStatus.PAID, album(0));
            when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));

            Order result = service.changeStatus(ORDER_NUMBER, OrderStatus.PREPARING, "출고 준비");

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("허용 대상이지만 불법 전이(PAID→SHIPPED) → 409 ORDER_INVALID_STATE_TRANSITION")
        void illegalTransition_conflict() {
            Order order = orderAt(OrderStatus.PAID, album(0));
            when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.changeStatus(ORDER_NUMBER, OrderStatus.SHIPPED, "사유"))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("지원하지 않는 대상 상태(CANCELLED) → 422 DOMAIN_RULE_VIOLATION, 저장소 미접근")
        void unsupportedTarget_unprocessable() {
            assertThatThrownBy(() -> service.changeStatus(ORDER_NUMBER, OrderStatus.CANCELLED, "환불"))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DOMAIN_RULE_VIOLATION);
            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("미존재 주문 → 404")
        void notFound() {
            when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changeStatus(ORDER_NUMBER, OrderStatus.PREPARING, "사유"))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("refund")
    class Refund {

        @Test
        @DisplayName("PAID 결제 → PG 환불 + Payment REFUNDED + Order CANCELLED + 재고 복원")
        void paidOrder_refunds() {
            Album album = album(0);
            Order order = orderAt(OrderStatus.PAID, album);
            Payment payment = paidPaymentFor(order);
            Instant refundedAt = Instant.parse("2026-05-13T10:00:00Z");
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.of(payment));
            when(paymentGateway.refund(any())).thenReturn(new RefundResponse(PG_TX, PaymentStatus.REFUNDED, refundedAt));

            RefundResult result = service.refund(ORDER_NUMBER, "운영 환불");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelledReason()).isEqualTo("운영 환불");
            assertThat(album.getStock()).isEqualTo(QTY);
            assertThat(result.alreadyRefunded()).isFalse();
            assertThat(result.refundedAt()).isEqualTo(refundedAt);

            ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
            verify(paymentGateway).refund(captor.capture());
            assertThat(captor.getValue().pgTransactionId()).isEqualTo(PG_TX);
            assertThat(captor.getValue().amount()).isEqualTo(ORDER_AMOUNT);
            assertThat(captor.getValue().reason()).isEqualTo("운영 환불");
            // #72: 결정적 멱등 키 전달 — 보상 트랜잭션 재시도 시 PG 캐시 응답 보장.
            assertThat(captor.getValue().idempotencyKey()).isEqualTo(EXPECTED_IDEM_KEY);
        }

        @Test
        @DisplayName("이미 REFUNDED 인 결제 → 멱등 응답 (PG 미호출, 상태/재고 불변)")
        void alreadyRefunded_idempotent() {
            Album album = album(5);
            Order order = orderAt(OrderStatus.PAID, album);
            Payment payment = paidPaymentFor(order);
            payment.markRefunded();
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.of(payment));

            RefundResult result = service.refund(ORDER_NUMBER, "다시 환불");

            assertThat(result.alreadyRefunded()).isTrue();
            assertThat(result.refundedAt()).isNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(album.getStock()).isEqualTo(5);
            verifyNoInteractions(paymentGateway);
        }

        @Test
        @DisplayName("PENDING 결제 → 409 PAYMENT_NOT_REFUNDABLE (PG 미호출, 불변)")
        void pendingPayment_conflict() {
            Album album = album(0);
            Order order = pendingOrder(album);
            Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", PG_TX);
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> service.refund(ORDER_NUMBER, "환불"))
                    .isInstanceOf(PaymentNotRefundableException.class);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(album.getStock()).isZero();
            verifyNoInteractions(paymentGateway);
        }

        @Test
        @DisplayName("FAILED 결제 → 409 PAYMENT_NOT_REFUNDABLE")
        void failedPayment_conflict() {
            Album album = album(0);
            Order order = pendingOrder(album);
            Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", PG_TX);
            payment.markFailed("카드 거절");
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> service.refund(ORDER_NUMBER, "환불"))
                    .isInstanceOf(PaymentNotRefundableException.class);
            verifyNoInteractions(paymentGateway);
        }

        @Test
        @DisplayName("주문이 CANCELLED 로 전이 불가(SHIPPED) → 409, PG 미호출, 불변")
        void orderNotCancellable_conflict() {
            Album album = album(0);
            Order order = orderAt(OrderStatus.SHIPPED, album);
            Payment payment = paidPaymentFor(order);
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> service.refund(ORDER_NUMBER, "환불"))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(album.getStock()).isZero();
            verifyNoInteractions(paymentGateway);
        }

        @Test
        @DisplayName("미존재 주문 → 404")
        void orderNotFound() {
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refund(ORDER_NUMBER, "환불"))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("결제 없음 → 404 PAYMENT_NOT_FOUND")
        void paymentNotFound() {
            Order order = orderAt(OrderStatus.PAID, album(0));
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refund(ORDER_NUMBER, "환불"))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("PG 환불 호출 실패 → 502 PaymentGatewayException, 상태/재고 미변경 (트랜잭션 롤백 전제)")
        void gatewayFailure_bubblesAsExternal() {
            Album album = album(0);
            Order order = orderAt(OrderStatus.PAID, album);
            Payment payment = paidPaymentFor(order);
            when(orderRepository.findWithAlbumsByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderIdForUpdate(any())).thenReturn(Optional.of(payment));
            when(paymentGateway.refund(any())).thenThrow(new IllegalStateException("PG down"));

            assertThatThrownBy(() -> service.refund(ORDER_NUMBER, "환불"))
                    .isInstanceOf(PaymentGatewayException.class);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(album.getStock()).isZero();
        }
    }
}
