package com.groove.payment.application;

import com.groove.common.idempotency.exception.IdempotencyConflictException;
import com.groove.order.domain.Order;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.api.dto.TossWebhookRequest;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.GatewayQuery;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("TossWebhookService 단위 테스트")
class TossWebhookServiceTest {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final String PAYMENT_KEY = "tviva20260623ABCD";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentSettlementService settlementService;

    @InjectMocks
    private TossWebhookService service;

    private static TossWebhookRequest webhook(String eventType, String paymentKey) {
        return new TossWebhookRequest(eventType, new TossWebhookRequest.Data(paymentKey));
    }

    private static Payment pendingPayment() {
        Order order = Order.placeForMember("ORD-20260623-A1", 1L, OrderFixtures.sampleShippingInfo());
        return Payment.initiate(order, 35000L, PaymentMethod.CARD, "TOSS", PAYMENT_KEY);
    }

    private static Payment paidPayment() {
        Payment payment = pendingPayment();
        payment.markPaid(Instant.parse("2026-06-23T12:00:00Z"));
        return payment;
    }

    private static Payment failedPayment() {
        Payment payment = pendingPayment();
        payment.markFailed("정산 타임아웃");   // reconciliation 이 timeout 으로 실패 처리한 결제
        return payment;
    }

    private PaymentCallbackResult applied(PaymentStatus status) {
        return new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 1L, PAYMENT_KEY, status);
    }

    @Test
    @DisplayName("로컬 PENDING + 재조회 PAID(금액 일치) → settle(PAID) 위임, APPLIED")
    void handle_paid_settles() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.PAID, 35000L));
        given(settlementService.settle(PAYMENT_KEY, PaymentStatus.PAID, null)).willReturn(applied(PaymentStatus.PAID));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verify(settlementService).settle(PAYMENT_KEY, PaymentStatus.PAID, null);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.APPLIED);
    }

    @Test
    @DisplayName("재조회 PAID 금액 불일치 → 정산 보류, 수동 확인 IGNORED (#320)")
    void handle_paidAmountMismatch_ignored() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        // 저장 금액 35000 과 다른 정산금액 → 자동 전이 금지.
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.PAID, 999000L));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verifyNoInteractions(settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("재조회 PAID 금액 미보고(null) → 검증 생략, 정상 settle(PAID) (#320)")
    void handle_paidAmountNull_settles() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.PAID, null));
        given(settlementService.settle(PAYMENT_KEY, PaymentStatus.PAID, null)).willReturn(applied(PaymentStatus.PAID));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verify(settlementService).settle(PAYMENT_KEY, PaymentStatus.PAID, null);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.APPLIED);
    }

    @Test
    @DisplayName("재조회 FAILED → settle(FAILED, 사유) 위임")
    void handle_failed_settlesFailed() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.FAILED, null));
        given(settlementService.settle(PAYMENT_KEY, PaymentStatus.FAILED, "토스 웹훅 결제 실패"))
                .willReturn(applied(PaymentStatus.FAILED));

        service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verify(settlementService).settle(PAYMENT_KEY, PaymentStatus.FAILED, "토스 웹훅 결제 실패");
    }

    @Test
    @DisplayName("로컬 결제 없음(위조·미연결 키) → 재조회 없이 IGNORED, outbound 미발생")
    void handle_localPaymentAbsent_ignoredWithoutQuery() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.empty());

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verifyNoInteractions(paymentGateway, settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("로컬 결제가 이미 종착 → 재조회·정산 없이 ALREADY_PROCESSED")
    void handle_localTerminal_alreadyProcessedWithoutQuery() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(paidPayment()));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verifyNoInteractions(paymentGateway, settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("결제 timeout 으로 FAILED 종착된 뒤 늦은 PAID 웹훅 도착 → 재조회·정산 없이 ALREADY_PROCESSED")
    void handle_lateWebhookAfterTimeoutFailure_alreadyProcessed() {
        // 이미 종착(FAILED)이라 재조회 없이 그대로 둔다. 늦게 온 PAID 웹훅이 결제를 되살리면 안 된다.
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(failedPayment()));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verifyNoInteractions(paymentGateway, settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("재조회가 비종착(PENDING)이면 정산하지 않고 IGNORED")
    void handle_pending_ignored() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.PENDING, null));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verifyNoInteractions(settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("재조회 REFUNDED 등 PAID/FAILED 외 종착은 정산하지 않는다(취소 흐름 범위 외)")
    void handle_refunded_ignored() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.REFUNDED, null));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        verifyNoInteractions(settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("동시 처리 충돌(IdempotencyConflictException)은 흡수해 IGNORED(200), 토스 재전송 폭주 방지")
    void handle_idempotencyConflict_absorbed() {
        given(paymentRepository.findByPgTransactionId(PAYMENT_KEY)).willReturn(Optional.of(pendingPayment()));
        given(paymentGateway.query(PAYMENT_KEY)).willReturn(new GatewayQuery(PaymentStatus.PAID, 35000L));
        given(settlementService.settle(PAYMENT_KEY, PaymentStatus.PAID, null))
                .willThrow(new IdempotencyConflictException("payment-callback:" + PAYMENT_KEY));

        PaymentCallbackResult result = service.handle(webhook(PAYMENT_STATUS_CHANGED, PAYMENT_KEY));

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("본문 전체 누락(null)은 거부 대신 무해 무시(200 IGNORED), outbound 미발생")
    void handle_nullBody_ignored() {
        PaymentCallbackResult result = service.handle(null);

        verifyNoInteractions(paymentRepository, paymentGateway, settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("대상 외 이벤트는 선조회·재조회·정산 없이 무시")
    void handle_otherEvent_ignored() {
        PaymentCallbackResult result = service.handle(webhook("DEPOSIT_CALLBACK", PAYMENT_KEY));

        verifyNoInteractions(paymentRepository, paymentGateway, settlementService);
        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
    }

    @Test
    @DisplayName("data.paymentKey 또는 data 누락이면 거부(400) 대신 무해 무시(200 IGNORED), outbound 미발생")
    void handle_missingPaymentKey_ignored() {
        assertThat(service.handle(webhook(PAYMENT_STATUS_CHANGED, null)).outcome())
                .isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
        assertThat(service.handle(new TossWebhookRequest(PAYMENT_STATUS_CHANGED, null)).outcome())
                .isEqualTo(PaymentCallbackResult.Outcome.IGNORED);

        verifyNoInteractions(paymentGateway, settlementService);
    }
}
