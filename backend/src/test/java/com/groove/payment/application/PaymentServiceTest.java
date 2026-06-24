package com.groove.payment.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.application.PaymentRequestSteps.PaymentRequestPrep;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentRequest;
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

/**
 * 결제 요청 오케스트레이터 단위 테스트 — prepare → PG 호출 → persist 흐름과 PG/충돌 처리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";
    private static final long ORDER_AMOUNT = 35_000L;
    private static final long ORDER_ID = 10L;

    @Mock
    private PaymentRequestSteps steps;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(steps, paymentGateway, paymentRepository);
    }

    private PaymentCreateRequest request() {
        return new PaymentCreateRequest(ORDER_NUMBER, PaymentMethod.CARD);
    }

    private PaymentApiResponse pendingResponse() {
        return new PaymentApiResponse(1L, ORDER_NUMBER, ORDER_AMOUNT, PaymentStatus.PENDING, PaymentMethod.CARD, "MOCK", null, null);
    }

    @Test
    @DisplayName("requestPayment: proceed → PG 를 (orderNumber, payable) 로 호출하고 persist 결과 반환")
    void requestPayment_proceed_callsGatewayThenPersist() {
        when(steps.prepare(1L, request())).thenReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, ORDER_AMOUNT));
        PaymentResponse pg = new PaymentResponse("mock-tx-1", PaymentStatus.PENDING, "MOCK");
        when(paymentGateway.request(any())).thenReturn(pg);
        PaymentApiResponse persisted = pendingResponse();
        when(steps.persist(any(), any(), any())).thenReturn(persisted);

        PaymentApiResponse response = paymentService.requestPayment(1L, request());

        assertThat(response).isSameAs(persisted);
        ArgumentCaptor<PaymentRequest> pgReq = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentGateway).request(pgReq.capture());
        assertThat(pgReq.getValue().orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(pgReq.getValue().amount()).isEqualTo(ORDER_AMOUNT);
        verify(steps).persist(any(PaymentRequestPrep.class), any(), any());
    }

    @Test
    @DisplayName("requestPayment: 기존 결제(existing) → PG·persist 미호출, 기존 응답 반환")
    void requestPayment_existing_returnsWithoutGateway() {
        PaymentApiResponse existing = pendingResponse();
        when(steps.prepare(1L, request())).thenReturn(PaymentRequestPrep.existing(existing, null));

        PaymentApiResponse response = paymentService.requestPayment(1L, request());

        assertThat(response).isSameAs(existing);
        verify(paymentGateway, never()).request(any());
        verify(steps, never()).persist(any(), any(), any());
    }

    @Test
    @DisplayName("requestPayment: PG 호출 실패 → PaymentGatewayException 으로 정규화, persist 미호출")
    void requestPayment_gatewayThrows_wrappedAndNoPersist() {
        when(steps.prepare(1L, request())).thenReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, ORDER_AMOUNT));
        when(paymentGateway.request(any())).thenThrow(new RuntimeException("PG down"));

        assertThatThrownBy(() -> paymentService.requestPayment(1L, request()))
                .isInstanceOf(PaymentGatewayException.class);

        verify(steps, never()).persist(any(), any(), any());
    }

    @Test
    @DisplayName("requestPayment: persist 가 uk_payment_order 충돌 → 기존 결제 재조회로 멱등 복원")
    void requestPayment_persistConflict_recoversExisting() {
        when(steps.prepare(1L, request())).thenReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, ORDER_AMOUNT));
        when(paymentGateway.request(any())).thenReturn(new PaymentResponse("mock-tx-1", PaymentStatus.PENDING, "MOCK"));
        when(steps.persist(any(), any(), any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk"));
        PaymentApiResponse recovered = pendingResponse();
        when(steps.findExistingForOrder(ORDER_ID)).thenReturn(Optional.of(recovered));

        PaymentApiResponse response = paymentService.requestPayment(1L, request());

        assertThat(response).isSameAs(recovered);
    }

    @Test
    @DisplayName("requestPayment: persist 충돌인데 기존 결제도 없으면(이론상) 충돌 예외 전파")
    void requestPayment_persistConflict_noExisting_rethrows() {
        when(steps.prepare(1L, request())).thenReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, ORDER_AMOUNT));
        when(paymentGateway.request(any())).thenReturn(new PaymentResponse("mock-tx-1", PaymentStatus.PENDING, "MOCK"));
        when(steps.persist(any(), any(), any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk"));
        when(steps.findExistingForOrder(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.requestPayment(1L, request()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // --- findForMember ---

    private Order order(boolean guest, Long memberId) {
        Order order = Mockito.mock(Order.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(order.getOrderNumber()).thenReturn(ORDER_NUMBER);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING);
        when(order.isGuestOrder()).thenReturn(guest);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }

    @Test
    @DisplayName("findForMember: 본인 주문 결제 → 응답 반환")
    void findForMember_owned_returnsResponse() {
        Order order = order(false, 1L);
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
        Order order = order(false, 1L);
        Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", "mock-tx-1");
        when(paymentRepository.findById(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findForMember(2L, 99L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("findForMember: 게스트 주문 결제는 회원이 조회할 수 없음 → 404")
    void findForMember_guestOrderPayment_throwsNotFound() {
        Order order = order(true, null);
        Payment payment = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.MOCK, "MOCK", "mock-tx-1");
        when(paymentRepository.findById(99L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findForMember(1L, 99L))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
