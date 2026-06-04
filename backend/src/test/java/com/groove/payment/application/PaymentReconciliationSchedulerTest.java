package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.order.domain.Order;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentReconciliationScheduler 단위 테스트")
class PaymentReconciliationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");
    private static final Duration MIN_AGE = Duration.ofMinutes(1);
    private static final int BATCH_SIZE = 200;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentCallbackService callbackService;
    @Mock
    private IdempotencyService idempotencyService;

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReconciliationScheduler(paymentRepository, paymentGateway, callbackService,
                idempotencyService, Clock.fixed(NOW, ZoneOffset.UTC), MIN_AGE, BATCH_SIZE);
    }

    private static Payment pending(String pgTransactionId) {
        Order order = Order.placeForMember("ORD-20260512-A1B2C3", 1L, com.groove.support.OrderFixtures.sampleShippingInfo());
        return Payment.initiate(order, 35000L, PaymentMethod.CARD, "MOCK", pgTransactionId);
    }

    private void passSupplierThrough() {
        given(idempotencyService.execute(anyString(), eq(PaymentCallbackResult.class), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        given(callbackService.applyResult(anyString(), any(), any()))
                .willReturn(new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 1L, "tx", PaymentStatus.PAID));
    }

    @Test
    @DisplayName("PG 가 PAID 를 반환하면 콜백 서비스로 동기화한다 (cutoff = now - min-age, batch-size 만큼 조회)")
    void reconcile_pgPaid_syncsViaCallback() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(
                eq(PaymentStatus.PENDING), eq(NOW.minus(MIN_AGE)), eq(Limit.of(BATCH_SIZE))))
                .willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PAID);
        passSupplierThrough();

        scheduler.reconcilePendingPayments();

        verify(idempotencyService).execute(eq("payment-callback:mock-tx-1"), eq(PaymentCallbackResult.class), any());
        verify(callbackService).applyResult("mock-tx-1", PaymentStatus.PAID, null);
    }

    @Test
    @DisplayName("PG 가 아직 PENDING 이면 콜백을 호출하지 않는다")
    void reconcile_pgPending_skips() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PENDING);

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(idempotencyService, callbackService);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 처리한다 (스케줄러 스레드로 예외 미전파)")
    void reconcile_oneFailure_continues() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any()))
                .willReturn(List.of(pending("mock-tx-bad"), pending("mock-tx-good")));
        given(paymentGateway.query("mock-tx-bad")).willThrow(new RuntimeException("PG 일시 장애"));
        given(paymentGateway.query("mock-tx-good")).willReturn(PaymentStatus.PAID);
        passSupplierThrough();

        assertThatCode(() -> scheduler.reconcilePendingPayments()).doesNotThrowAnyException();

        verify(callbackService).applyResult("mock-tx-good", PaymentStatus.PAID, null);
        verify(callbackService, never()).applyResult(eq("mock-tx-bad"), any(), any());
    }

    @Test
    @DisplayName("대상이 없으면 PG·콜백을 건드리지 않는다")
    void reconcile_noStalePayments_noop() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of());

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(paymentGateway, idempotencyService, callbackService);
    }
}
