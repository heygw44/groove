package com.groove.payment.application;

import com.groove.common.exception.DomainException;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";
    private static final long ORDER_AMOUNT = 35_000L;
    private static final long ORDER_ID = 10L;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentGateway paymentGateway;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderRepository, paymentGateway);
    }

    /** lenient 모드 Order 목 — 테스트마다 사용하는 getter 가 달라 strict 모드면 UnnecessaryStubbing 으로 깨진다. */
    private Order order(boolean guest, Long memberId, OrderStatus status) {
        Order order = Mockito.mock(Order.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getOrderNumber()).thenReturn(ORDER_NUMBER);
        when(order.getStatus()).thenReturn(status);
        when(order.getTotalAmount()).thenReturn(ORDER_AMOUNT);
        when(order.isGuestOrder()).thenReturn(guest);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }

    private PaymentCreateRequest request() {
        return new PaymentCreateRequest(ORDER_NUMBER, PaymentMethod.CARD);
    }

    @Test
    @DisplayName("requestPayment: 회원 본인 PENDING 주문 → PENDING 결제 저장 후 응답")
    void requestPayment_member_success() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentGateway.request(any())).thenReturn(new PaymentResponse("mock-tx-1", PaymentStatus.PENDING, "MOCK"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentApiResponse response = paymentService.requestPayment(1L, request());

        assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(response.amount()).isEqualTo(ORDER_AMOUNT);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.pgProvider()).isEqualTo("MOCK");
        assertThat(response.paidAt()).isNull();

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getValue().getPgTransactionId()).isEqualTo("mock-tx-1");
    }

    @Test
    @DisplayName("requestPayment: 게스트 주문 → 익명 호출자도 결제 접수")
    void requestPayment_guestOrder_anonymousCaller_success() {
        Order order = order(true, null, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentGateway.request(any())).thenReturn(new PaymentResponse("mock-tx-1", PaymentStatus.PENDING, "MOCK"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentApiResponse response = paymentService.requestPayment(null, new PaymentCreateRequest(ORDER_NUMBER, PaymentMethod.MOCK));

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo(PaymentMethod.MOCK);
        verify(paymentRepository).save(any());
    }

    @Test
    @DisplayName("requestPayment: 타 회원 주문 → 404, PG·저장 미호출")
    void requestPayment_memberOrder_otherCaller_throwsNotFound() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.requestPayment(2L, request()))
                .isInstanceOf(OrderNotFoundException.class);

        verify(paymentGateway, never()).request(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestPayment: 회원 주문에 익명 접근 → 404")
    void requestPayment_memberOrder_anonymousCaller_throwsNotFound() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.requestPayment(null, request()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("requestPayment: 미존재 주문 → 404")
    void requestPayment_unknownOrder_throwsNotFound() {
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.requestPayment(1L, request()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("requestPayment: 주문에 이미 결제가 있으면 기존 건 반환 (PG·저장 미호출)")
    void requestPayment_existingPayment_returnsExisting() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        Payment existing = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.BANK_TRANSFER, "MOCK", "mock-tx-existing");
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        PaymentApiResponse response = paymentService.requestPayment(1L, request());

        assertThat(response.method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentGateway, never()).request(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestPayment: PENDING 아닌 주문 → 409, PG·저장 미호출")
    void requestPayment_orderNotPending_throwsConflict() {
        Order order = order(false, 1L, OrderStatus.PAID);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.requestPayment(1L, request()))
                .isInstanceOf(IllegalStateTransitionException.class);

        verify(paymentGateway, never()).request(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestPayment: 결제 금액이 0 이하인 주문 → 422 DomainException, PG·저장 미호출")
    void requestPayment_zeroAmountOrder_throwsDomainException() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(order.getTotalAmount()).thenReturn(0L);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.requestPayment(1L, request()))
                .isInstanceOf(DomainException.class);

        verify(paymentGateway, never()).request(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestPayment: PG 호출 실패 → PaymentGatewayException 으로 정규화, 저장 미호출")
    void requestPayment_gatewayThrows_wrappedAsPaymentGatewayException() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentGateway.request(any())).thenThrow(new RuntimeException("PG down"));

        assertThatThrownBy(() -> paymentService.requestPayment(1L, request()))
                .isInstanceOf(PaymentGatewayException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("findForMember: 본인 주문 결제 → 응답 반환")
    void findForMember_owned_returnsResponse() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", "mock-tx-1");
        when(paymentRepository.findById(99L)).thenReturn(Optional.of(payment));

        PaymentApiResponse response = paymentService.findForMember(1L, 99L);

        assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findForMember: 미존재 결제 → 404")
    void findForMember_notFound() {
        when(paymentRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findForMember(1L, 99L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("findForMember: 타 회원 주문 결제 → 404")
    void findForMember_otherMember_throwsNotFound() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", "mock-tx-1");
        when(paymentRepository.findById(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findForMember(2L, 99L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("findForMember: 게스트 주문 결제는 회원이 조회할 수 없음 → 404")
    void findForMember_guestOrderPayment_throwsNotFound() {
        Order order = order(true, null, OrderStatus.PENDING);
        Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.MOCK, "MOCK", "mock-tx-1");
        when(paymentRepository.findById(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findForMember(1L, 99L))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
