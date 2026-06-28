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
import com.groove.payment.exception.PaymentCallbackTokenMismatchException;
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
    private static final String VALID_CALLBACK = "tok-2b8f1c6a-3d4e-4f5a";

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
        given(payment.getCallbackToken()).willReturn(VALID_CALLBACK);
        return payment;
    }

    /** idempotencyService.execute(key, Class, Supplier) 가 action 을 즉시 실행하도록 한다. */
    private void runIdempotencyInline() {
        given(idempotencyService.execute(anyString(), eq(PaymentCallbackResult.class), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    // --- checkout ---

    @Test
    @DisplayName("checkout: 게이트웨이 request() 없이 잠정 pgTx=orderNumber·provider=TOSS·콜백 토큰으로 PENDING 저장하고 위젯값 응답")
    void checkout_proceed_persistsPendingWithProvisionalPgTx() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, AMOUNT));
        given(steps.persist(any(), eq(PaymentMethod.CARD), any(), any())).willReturn(pendingResponse());
        given(tossProperties.getIfAvailable()).willReturn(null); // test 프로파일 — clientKey/URL 없음

        TossCheckoutResponse response = service.checkout(1L, request());

        assertThat(response.orderId()).isEqualTo(ORDER_NUMBER);
        assertThat(response.amount()).isEqualTo(AMOUNT);
        assertThat(response.clientKey()).isNull();
        assertThat(response.successUrl()).isNull(); // props 부재 → URL null
        assertThat(response.failUrl()).isNull();

        ArgumentCaptor<PaymentResponse> pg = ArgumentCaptor.forClass(PaymentResponse.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(steps).persist(any(PaymentRequestPrep.class), eq(PaymentMethod.CARD), pg.capture(), tokenCaptor.capture());
        assertThat(pg.getValue().pgTransactionId()).isEqualTo(TossPaymentService.PENDING_PG_TX_PREFIX + ORDER_NUMBER); // 잠정 pgTx
        assertThat(pg.getValue().provider()).isEqualTo("TOSS");
        assertThat(pg.getValue().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(tokenCaptor.getValue()).isNotBlank(); // 결제별 콜백 토큰 발급·저장
        verify(paymentGateway, never()).request(any()); // 토스는 request() 미사용
    }

    @Test
    @DisplayName("checkout: dev props 존재 → successUrl 에만 발급 토큰 쿼리 포함, failUrl 은 토큰 없음(#309)")
    void checkout_proceed_buildsCallbackUrlsWithToken() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, AMOUNT));
        given(steps.persist(any(), eq(PaymentMethod.CARD), any(), any())).willReturn(pendingResponse());
        TossPaymentProperties props = new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc",
                "http://localhost:8080/payments/toss/success", "http://localhost:8080/payments/toss/fail", null, null);
        given(tossProperties.getIfAvailable()).willReturn(props);

        TossCheckoutResponse response = service.checkout(1L, request());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(steps).persist(any(), eq(PaymentMethod.CARD), any(), tokenCaptor.capture());
        String token = tokenCaptor.getValue();
        assertThat(response.clientKey()).isEqualTo("test_ck_abc");
        assertThat(response.successUrl()).isEqualTo("http://localhost:8080/payments/toss/success?token=" + token);
        assertThat(response.failUrl()).isEqualTo("http://localhost:8080/payments/toss/fail"); // fail 은 토큰 미부착
    }

    @Test
    @DisplayName("checkout: 기존 결제(existing) → persist 미호출, prep 에 실린 저장 토큰으로 재구성한 successUrl 응답(추가 조회 0건, #309)")
    void checkout_existing_returnsClientKeyWithoutPersist() {
        // 멱등 경로는 prepare 가 이미 로드한 저장 토큰을 prep 으로 받아 그대로 쓴다 — order/payment 재조회 없음.
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.existing(pendingResponse(), VALID_CALLBACK));
        TossPaymentProperties props = new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, null);
        given(tossProperties.getIfAvailable()).willReturn(props);

        TossCheckoutResponse response = service.checkout(1L, request());

        assertThat(response.clientKey()).isEqualTo("test_ck_abc");
        assertThat(response.orderId()).isEqualTo(ORDER_NUMBER);
        assertThat(response.successUrl()).isEqualTo("http://s?token=" + VALID_CALLBACK); // 저장 토큰 재사용
        assertThat(response.failUrl()).isEqualTo("http://f"); // fail 은 토큰 미부착
        verify(steps, never()).persist(any(), any(), any(), any());
        verifyNoInteractions(orderRepository, paymentRepository); // 멱등 경로 토큰 재조회 0건
    }

    @Test
    @DisplayName("checkout: persist 가 uk_payment_order 충돌 → 기존 결제 재조회로 멱등 복원")
    void checkout_persistConflict_recoversExisting() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, AMOUNT));
        given(steps.persist(any(), any(), any(), any())).willThrow(new DataIntegrityViolationException("uk"));
        given(steps.findExistingForOrder(ORDER_ID)).willReturn(Optional.of(PaymentRequestPrep.existing(pendingResponse(), null)));
        given(tossProperties.getIfAvailable()).willReturn(null);

        TossCheckoutResponse response = service.checkout(1L, request());

        assertThat(response.orderId()).isEqualTo(ORDER_NUMBER);
        assertThat(response.amount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("checkout: persist 동시 충돌 시 successUrl 토큰은 우리 로컬 토큰이 아니라 '저장된' 기존 결제 토큰을 쓴다")
    void checkout_persistConflict_usesStoredTokenNotLocal() {
        given(steps.prepare(1L, request())).willReturn(PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, AMOUNT));
        given(steps.persist(any(), any(), any(), any())).willThrow(new DataIntegrityViolationException("uk"));
        // 충돌 복원 경로는 findExistingForOrder 가 "승자" 결제의 응답과 저장 토큰(VALID_CALLBACK)을 함께 실어 준다 — 추가 재조회 없음.
        given(steps.findExistingForOrder(ORDER_ID))
                .willReturn(Optional.of(PaymentRequestPrep.existing(pendingResponse(), VALID_CALLBACK)));
        TossPaymentProperties props = new TossPaymentProperties(
                null, "test_ck_abc", "test_sk_abc", "http://s", "http://f", null, null);
        given(tossProperties.getIfAvailable()).willReturn(props);

        TossCheckoutResponse response = service.checkout(1L, request());

        // 로컬 랜덤 토큰이 아니라 저장된 토큰이 successUrl 에 박혀야 confirm 이 통과한다(동시성 버그 회귀 방지).
        assertThat(response.successUrl()).isEqualTo("http://s?token=" + VALID_CALLBACK);
        assertThat(response.failUrl()).isEqualTo("http://f"); // fail 은 토큰 미부착
        verifyNoInteractions(orderRepository, paymentRepository); // 충돌 복원도 토큰 재조회 0건
    }

    // --- confirm ---

    @Test
    @DisplayName("confirm: 금액 일치 → 게이트웨이 confirm 후 applyConfirmedPaid 위임")
    void confirm_paid_verifiesAmountThenApplies() {
        // 경계: 게스트 토큰이 누출돼 토큰 검증을 통과하더라도, PAID 전이는 게이트웨이 confirm(토스가 발급한 유효 paymentKey)이
        // PAID 를 반환해야만 일어난다. 즉 토큰만으로는 PAID 강제 불가 — 유효 paymentKey 가 핵심 관문이다(위조 paymentKey 거부는 실 게이트웨이/dev·prod 책임, mock 으론 재현 불가).
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));
        runIdempotencyInline();
        given(paymentGateway.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT))
                .willReturn(new ConfirmResponse(PAYMENT_KEY, PaymentStatus.PAID, PaymentMethod.VIRTUAL_ACCOUNT));
        given(callbackService.applyConfirmedPaid(ORDER_ID, PAYMENT_KEY, AMOUNT, PaymentMethod.VIRTUAL_ACCOUNT))
                .willReturn(new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 42L, PAYMENT_KEY, PaymentStatus.PAID));

        PaymentCallbackResult result = service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, VALID_CALLBACK);

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.APPLIED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentGateway).confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT);
        // confirm 응답의 실제 수단(가상계좌)이 적용 경로로 스레딩된다.
        verify(callbackService).applyConfirmedPaid(ORDER_ID, PAYMENT_KEY, AMOUNT, PaymentMethod.VIRTUAL_ACCOUNT);
    }

    @Test
    @DisplayName("confirm: 저장 payable != amount → confirm 호출 없이 PaymentAmountMismatchException")
    void confirm_amountMismatch_rejectedBeforeGateway() {
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT + 1, VALID_CALLBACK))
                .isInstanceOf(PaymentAmountMismatchException.class);

        verifyNoInteractions(paymentGateway, callbackService, idempotencyService);
    }

    @Test
    @DisplayName("confirm: 토큰 불일치 → 금액검증·게이트웨이 전에 PaymentCallbackTokenMismatchException, 상태 전이 없음")
    void confirm_tokenMismatch_rejectedBeforeEverything() {
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT); // 저장 토큰 = VALID_CALLBACK
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, "wrong-token"))
                .isInstanceOf(PaymentCallbackTokenMismatchException.class);

        verifyNoInteractions(paymentGateway, callbackService, idempotencyService);
    }

    @Test
    @DisplayName("confirm: 토큰 누락(null) → 거부")
    void confirm_tokenMissing_rejected() {
        Order order = orderMock();
        Payment pending = paymentMock(PaymentStatus.PENDING, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, null))
                .isInstanceOf(PaymentCallbackTokenMismatchException.class);

        verifyNoInteractions(paymentGateway, callbackService, idempotencyService);
    }

    @Test
    @DisplayName("confirm: 이미 PAID(새로고침) → 빠른 멱등 경로, 게이트웨이·멱등 래퍼 미호출")
    void confirm_alreadyPaid_fastPathIdempotent() {
        Order order = orderMock();
        Payment paid = paymentMock(PaymentStatus.PAID, AMOUNT);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

        PaymentCallbackResult result = service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, VALID_CALLBACK);

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
                .willReturn(new ConfirmResponse(PAYMENT_KEY, PaymentStatus.PENDING, PaymentMethod.VIRTUAL_ACCOUNT));

        assertThatThrownBy(() -> service.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, VALID_CALLBACK))
                .isInstanceOf(PaymentGatewayException.class);

        // 후속 웹훅/폴링이 정산하도록 연결하며, confirm 시점의 실제 수단(가상계좌)도 함께 보정한다.
        verify(callbackService).linkPendingPaymentKey(ORDER_ID, PAYMENT_KEY, PaymentMethod.VIRTUAL_ACCOUNT);
        verify(callbackService, never()).applyConfirmedPaid(anyLong(), anyString(), anyLong(), any());
    }
}
