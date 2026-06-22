package com.groove.payment.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.api.dto.TossCheckoutResponse;
import com.groove.payment.application.PaymentRequestSteps.PaymentRequestPrep;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentAmountMismatchException;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.gateway.ConfirmResponse;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentResponse;
import com.groove.payment.gateway.TossPaymentProperties;
import com.groove.common.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

/**
 * 토스 confirm 승인 흐름 오케스트레이터 단위 테스트 — checkout(잠정 pgTx 저장) · confirm(금액검증→게이트웨이→적용) · fail(보상).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TossPaymentService")
class TossPaymentServiceTest {

    private static final String ORDER_NUMBER = "ORD-20260622-A1B2C3";
    private static final String PAYMENT_KEY = "toss-payment-key-1";
    private static final long AMOUNT = 32_000L;
    private static final long ORDER_ID = 10L;

    @Mock private PaymentRequestSteps steps;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentCallbackService callbackService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ObjectProvider<TossPaymentProperties> tossProperties;

    private TossPaymentService service;

    @BeforeEach
    void setUp() {
        service = new TossPaymentService(steps, paymentGateway, callbackService, idempotencyService,
                orderRepository, paymentRepository, tossProperties);
    }

    private PaymentCreateRequest request() {
        return new PaymentCreateRequest(ORDER_NUMBER, PaymentMethod.CARD);
    }

    private PaymentApiResponse pendingResponse() {
        return new PaymentApiResponse(1L, ORDER_NUMBER, AMOUNT, PaymentStatus.PENDING, PaymentMethod.CARD, "TOSS", null, null);
    }

    private Order orderMock() {
        Order order = mock(Order.class, withSettings().strictness(Strictness.LENIENT));
        given(order.getId()).willReturn(ORDER_ID);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        return order;
    }

    private Payment paymentMock(PaymentStatus status, long amount) {
        Payment payment = mock(Payment.class, withSettings().strictness(Strictness.LENIENT));
        given(payment.getStatus()).willReturn(status);
        given(payment.getAmount()).willReturn(amount);
        given(payment.getId()).willReturn(42L);
        given(payment.getPgTransactionId()).willReturn(PAYMENT_KEY);
        return payment;
    }

    /** idempotencyService.execute(key, Class, Supplier) 가 action 을 즉시 실행하도록 한다. */
    private void runIdempotencyInline() {
        given(idempotencyService.execute(anyString(), eq(PaymentCallbackResult.class), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    // --- checkout ---

    @Test
    @DisplayName("checkout: 게이트웨이 request() 없이 잠정 pgTx=orderNumber·provider=TOSS 로 PENDING 저장하고 위젯값 응답")
    void checkout_proceed_persistsPendingWithProvisionalPgTx() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, AMOUNT));
        given(steps.persist(any(), eq(PaymentMethod.CARD), any())).willReturn(pendingResponse());
        given(tossProperties.getIfAvailable()).willReturn(null); // test 프로파일 — clientKey 없음

        TossCheckoutResponse response = service.checkout(1L, request());

        assertThat(response.orderId()).isEqualTo(ORDER_NUMBER);
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.clientKey()).isNull();

        ArgumentCaptor<PaymentResponse> pg = ArgumentCaptor.forClass(PaymentResponse.class);
        verify(steps).persist(any(PaymentRequestPrep.class), eq(PaymentMethod.CARD), pg.capture());
        assertThat(pg.getValue().pgTransactionId()).isEqualTo(TossPaymentService.PENDING_PG_TX_PREFIX + ORDER_NUMBER); // 잠정 pgTx
        assertThat(pg.getValue().provider()).isEqualTo("TOSS");
        assertThat(pg.getValue().status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentGateway, never()).request(any()); // 토스는 request() 미사용
    }

    @Test
    @DisplayName("checkout: 기존 결제(existing) → persist 미호출, dev clientKey 포함 응답")
    void checkout_existing_returnsClientKeyWithoutPersist() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.existing(pendingResponse()));
        TossPaymentProperties props = new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, null);
        given(tossProperties.getIfAvailable()).willReturn(props);

        TossCheckoutResponse response = service.checkout(1L, request());

        assertThat(response.clientKey()).isEqualTo("test_ck_abc");
        assertThat(response.orderId()).isEqualTo(ORDER_NUMBER);
        verify(steps, never()).persist(any(), any(), any());
    }

    @Test
    @DisplayName("checkout: persist 가 uk_payment_order 충돌 → 기존 결제 재조회로 멱등 복원")
    void checkout_persistConflict_recoversExisting() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, AMOUNT));
        given(steps.persist(any(), any(), any())).willThrow(new DataIntegrityViolationException("uk"));
        given(steps.findExistingForOrder(ORDER_ID)).willReturn(Optional.of(pendingResponse()));
        given(tossProperties.getIfAvailable()).willReturn(null);

        TossCheckoutResponse response = service.checkout(1L, request());

        assertThat(response.orderId()).isEqualTo(ORDER_NUMBER);
        assertThat(response.amount()).isEqualTo(AMOUNT);
    }

    // --- confirm ---

    @Test
    @DisplayName("confirm: 금액 일치 → 게이트웨이 confirm 후 applyConfirmedPaid 위임")
    void confirm_paid_verifiesAmountThenApplies() {
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));
        runIdempotencyInline();
        given(paymentGateway.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT))
                .willReturn(new ConfirmResponse(PAYMENT_KEY, PaymentStatus.PAID));
        given(callbackService.applyConfirmedPaid(ORDER_ID, PAYMENT_KEY, AMOUNT))
                .willReturn(new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 42L, PAYMENT_KEY, PaymentStatus.PAID));

        PaymentCallbackResult result = service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT);

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.APPLIED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentGateway).confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT);
        verify(callbackService).applyConfirmedPaid(ORDER_ID, PAYMENT_KEY, AMOUNT);
    }

    @Test
    @DisplayName("confirm: 저장 payable != amount → confirm 호출 없이 PaymentAmountMismatchException")
    void confirm_amountMismatch_rejectedBeforeGateway() {
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT + 1))
                .isInstanceOf(PaymentAmountMismatchException.class);

        verifyNoInteractions(paymentGateway, callbackService, idempotencyService);
    }

    @Test
    @DisplayName("confirm: 이미 PAID(새로고침) → 빠른 멱등 경로, 게이트웨이·멱등 래퍼 미호출")
    void confirm_alreadyPaid_fastPathIdempotent() {
        Order order = orderMock();
        Payment paid = paymentMock(PaymentStatus.PAID, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

        PaymentCallbackResult result = service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT);

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.ALREADY_PROCESSED);
        verifyNoInteractions(paymentGateway, callbackService, idempotencyService);
    }

    @Test
    @DisplayName("confirm: 비-PAID 결과(가상계좌 PENDING) → paymentKey 연결 후 미확정 예외, PAID 미적용·종착 캐시 안 함")
    void confirm_nonPaidResult_linksKeyAndSignalsUnsettled() {
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));
        runIdempotencyInline();
        given(paymentGateway.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT))
                .willReturn(new ConfirmResponse(PAYMENT_KEY, PaymentStatus.PENDING));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT))
                .isInstanceOf(PaymentGatewayException.class);

        verify(callbackService).linkPendingPaymentKey(ORDER_ID, PAYMENT_KEY); // 후속 웹훅/폴링이 정산하도록 연결
        verify(callbackService, never()).applyConfirmedPaid(anyLong(), anyString(), anyLong());
    }

    // --- fail ---

    @Test
    @DisplayName("fail: 주문 단위 멱등 키로 applyConfirmFailure(보상) 위임, 사유에 code·message 포함")
    void fail_appliesCompensationWithOrderKeyedIdempotency() {
        Order order = orderMock();
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        runIdempotencyInline();
        given(callbackService.applyConfirmFailure(eq(ORDER_ID), anyString()))
                .willReturn(new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 42L, "order:" + ORDER_ID, PaymentStatus.FAILED));

        service.fail(ORDER_NUMBER, "PAY_PROCESS_CANCELED", "사용자취소");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).execute(key.capture(), eq(PaymentCallbackResult.class), any());
        assertThat(key.getValue()).isEqualTo("payment-callback-fail:" + ORDER_NUMBER);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(callbackService).applyConfirmFailure(eq(ORDER_ID), reason.capture());
        assertThat(reason.getValue()).contains("PAY_PROCESS_CANCELED").contains("사용자취소");
    }
}
