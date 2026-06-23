package com.groove.payment.application;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private static final Duration TOSS_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration MAX_AGE = Duration.ofHours(1);
    private static final int BATCH_SIZE = 200;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentSettlementService settlementService;

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReconciliationScheduler(paymentRepository, paymentGateway, settlementService,
                Clock.fixed(NOW, ZoneOffset.UTC), MIN_AGE, TOSS_TIMEOUT, MAX_AGE, BATCH_SIZE);
    }

    /** 만료 전(createdAt=NOW) generic PENDING — reconcileOne 이 createdAt 을 읽으므로 주입 필수. */
    private static Payment pending(String pgTransactionId) {
        return pending(pgTransactionId, NOW);
    }

    private static Payment pending(String pgTransactionId, Instant createdAt) {
        Order order = Order.placeForMember("ORD-20260512-A1B2C3", 1L, com.groove.support.OrderFixtures.sampleShippingInfo());
        Payment payment = Payment.initiate(order, 35000L, PaymentMethod.CARD, "MOCK", pgTransactionId);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt); // max-age 판정용 createdAt 주입
        return payment;
    }

    private static Payment tossPending(String pgTransactionId, Instant createdAt) {
        Order order = Order.placeForMember("ORD-20260512-A1B2C3", 1L, com.groove.support.OrderFixtures.sampleShippingInfo());
        Payment payment = Payment.initiate(order, 35000L, PaymentMethod.CARD, "TOSS", pgTransactionId);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt); // 만료 판정용 createdAt 주입
        return payment;
    }

    private void settleReturnsApplied() {
        given(settlementService.settle(anyString(), any(), any()))
                .willReturn(new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 1L, "tx", PaymentStatus.PAID));
    }

    @Test
    @DisplayName("PG 가 PAID 를 반환하면 공유 정산 헬퍼로 동기화한다 (cutoff = now - min-age, batch-size 만큼 조회)")
    void reconcile_pgPaid_syncsViaSettlement() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(
                eq(PaymentStatus.PENDING), eq(NOW.minus(MIN_AGE)), eq(Limit.of(BATCH_SIZE))))
                .willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PAID);
        settleReturnsApplied();

        scheduler.reconcilePendingPayments();

        verify(settlementService).settle("mock-tx-1", PaymentStatus.PAID, null);
    }

    @Test
    @DisplayName("PG 가 아직 PENDING 이면 정산 헬퍼를 호출하지 않는다")
    void reconcile_pgPending_skips() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PENDING);

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(settlementService);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 처리한다 (스케줄러 스레드로 예외 미전파)")
    void reconcile_oneFailure_continues() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any()))
                .willReturn(List.of(pending("mock-tx-bad"), pending("mock-tx-good")));
        given(paymentGateway.query("mock-tx-bad")).willThrow(new RuntimeException("PG 일시 장애"));
        given(paymentGateway.query("mock-tx-good")).willReturn(PaymentStatus.PAID);
        settleReturnsApplied();

        assertThatCode(() -> scheduler.reconcilePendingPayments()).doesNotThrowAnyException();

        verify(settlementService).settle("mock-tx-good", PaymentStatus.PAID, null);
        verify(settlementService, never()).settle(eq("mock-tx-bad"), any(), any());
    }

    @Test
    @DisplayName("PG 가 취소(REFUNDED)를 반환하면 미확정 결제를 FAILED 로 종결한다")
    void reconcile_pgRefunded_settlesFailed() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.REFUNDED);
        settleReturnsApplied();

        scheduler.reconcilePendingPayments();

        verify(settlementService).settle("mock-tx-1", PaymentStatus.FAILED, "PG 측 결제 취소 확인 — 미확정 결제 실패 처리");
    }

    @Test
    @DisplayName("PG 가 부분취소(PARTIALLY_REFUNDED)를 반환해도 미확정 결제를 FAILED 로 종결한다")
    void reconcile_pgPartialCanceled_settlesFailed() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PARTIALLY_REFUNDED);
        settleReturnsApplied();

        scheduler.reconcilePendingPayments();

        verify(settlementService).settle("mock-tx-1", PaymentStatus.FAILED, "PG 측 결제 취소 확인 — 미확정 결제 실패 처리");
    }

    @Test
    @DisplayName("max-age 초과여도 PG 가 PENDING(진행 중)을 답하면 종결하지 않고 다음 주기에 재시도한다 (#299 리뷰 #2)")
    void reconcile_expiredPendingPersists_doesNotSettle() {
        Payment expired = pending("mock-tx-1", NOW.minus(MAX_AGE).minus(Duration.ofMinutes(1)));
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(expired));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PENDING);

        scheduler.reconcilePendingPayments();

        // PG 가 정상적으로 '진행 중'이라 응답하면 max-age 초과여도 강제 실패시키지 않는다(오류 케이스만 종결).
        verifyNoInteractions(settlementService);
    }

    @Test
    @DisplayName("max-age 초과 결제의 query 가 계속 실패하면 FAILED 로 종결한다 (잔존 pgTx 404/502)")
    void reconcile_expiredQueryError_settlesFailed() {
        Payment expired = pending("mock-tx-stale", NOW.minus(MAX_AGE).minus(Duration.ofMinutes(1)));
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(expired));
        given(paymentGateway.query("mock-tx-stale")).willThrow(new RuntimeException("PG 404 → 502"));
        settleReturnsApplied();

        scheduler.reconcilePendingPayments();

        verify(settlementService).settle(
                "mock-tx-stale", PaymentStatus.FAILED, "결제 폴링 조회 반복 실패(max-age 초과) — 자동 실패 처리");
    }

    @Test
    @DisplayName("만료 전 결제의 query 가 실패하면 종결하지 않고 다음 주기에 재시도한다")
    void reconcile_freshQueryError_retriesNextCycle() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(pending("mock-tx-1")));
        given(paymentGateway.query("mock-tx-1")).willThrow(new RuntimeException("PG 일시 장애"));

        assertThatCode(() -> scheduler.reconcilePendingPayments()).doesNotThrowAnyException();

        verifyNoInteractions(settlementService);
    }

    @Test
    @DisplayName("max-age 초과 + query=PAID 인데 settle 이 실패해도 FAILED 로 뒤집지 않고 다음 주기에 재시도한다")
    void reconcile_expiredPaidButSettleThrows_doesNotForceFail() {
        Payment expired = pending("mock-tx-1", NOW.minus(MAX_AGE).minus(Duration.ofMinutes(1)));
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(expired));
        given(paymentGateway.query("mock-tx-1")).willReturn(PaymentStatus.PAID);
        given(settlementService.settle("mock-tx-1", PaymentStatus.PAID, null)).willThrow(new RuntimeException("일시 정산 실패"));

        assertThatCode(() -> scheduler.reconcilePendingPayments()).doesNotThrowAnyException();

        verify(settlementService).settle("mock-tx-1", PaymentStatus.PAID, null);
        // 핵심: PAID 확정 결제가 settle 일시 오류로 FAILED 로 뒤집히면 안 된다(#299 리뷰).
        verify(settlementService, never()).settle(eq("mock-tx-1"), eq(PaymentStatus.FAILED), any());
    }

    @Test
    @DisplayName("max-age 초과 종결의 settle 자체가 실패해도 예외를 전파하지 않는다 (best-effort)")
    void reconcile_terminateSettleThrows_swallowed() {
        Payment expired = pending("mock-tx-stale", NOW.minus(MAX_AGE).minus(Duration.ofMinutes(1)));
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of(expired));
        given(paymentGateway.query("mock-tx-stale")).willThrow(new RuntimeException("PG 404 → 502"));
        given(settlementService.settle(anyString(), any(), any())).willThrow(new RuntimeException("정산 DB 오류"));

        assertThatCode(() -> scheduler.reconcilePendingPayments()).doesNotThrowAnyException();

        verify(settlementService).settle(
                "mock-tx-stale", PaymentStatus.FAILED, "결제 폴링 조회 반복 실패(max-age 초과) — 자동 실패 처리");
    }

    @Test
    @DisplayName("max-age 가 min-age 이하이면 생성자가 거부한다")
    void constructor_maxAgeNotGreaterThanMinAge_throws() {
        assertThatThrownBy(() -> new PaymentReconciliationScheduler(paymentRepository, paymentGateway, settlementService,
                Clock.fixed(NOW, ZoneOffset.UTC), MIN_AGE, TOSS_TIMEOUT, MIN_AGE, BATCH_SIZE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("대상이 없으면 PG·정산 헬퍼를 건드리지 않는다")
    void reconcile_noStalePayments_noop() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any())).willReturn(List.of());

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(paymentGateway, settlementService);
    }

    @Test
    @DisplayName("토스 미확정 PENDING(toss-pending) 이 만료되면 query 대신 FAILED 만료 처리로 정리한다")
    void reconcile_tossExpiredPending_reapsFailed() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any()))
                .willReturn(List.of(tossPending("toss-pending:ORD-20260512-A1B2C3", NOW.minus(Duration.ofMinutes(21)))));

        scheduler.reconcilePendingPayments();

        verify(paymentGateway, never()).query(anyString()); // 토스는 query 폴링 비대상
        verify(settlementService).settle(
                "toss-pending:ORD-20260512-A1B2C3", PaymentStatus.FAILED, "토스 결제 미확정 만료 — 자동 실패 처리");
    }

    @Test
    @DisplayName("토스 미확정 PENDING 이 아직 만료 전이면 query·만료 처리 모두 하지 않는다")
    void reconcile_tossFreshPending_skips() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any()))
                .willReturn(List.of(tossPending("toss-pending:ORD-20260512-A1B2C3", NOW.minus(Duration.ofMinutes(5)))));

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(paymentGateway, settlementService);
    }

    @Test
    @DisplayName("토스이지만 실제 paymentKey 가 연결된(가상계좌) PENDING 은 만료 리퍼 대상이 아니다")
    void reconcile_tossLinkedPending_notReaped() {
        given(paymentRepository.findByStatusAndCreatedAtBefore(any(), any(), any()))
                .willReturn(List.of(tossPending("real-payment-key", NOW.minus(Duration.ofMinutes(21)))));

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(paymentGateway, settlementService);
    }
}
