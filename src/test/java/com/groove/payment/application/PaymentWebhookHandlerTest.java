package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.InvalidWebhookSignatureException;
import com.groove.payment.gateway.WebhookNotification;
import com.groove.payment.gateway.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookHandler 단위 테스트")
class PaymentWebhookHandlerTest {

    private static final WebhookNotification PAID_NOTIFICATION = new WebhookNotification(
            "mock-tx-1", "ORD-20260512-A1B2C3", PaymentStatus.PAID, Instant.parse("2026-05-12T10:00:00Z"), "secret");

    @Mock
    private WebhookSignatureVerifier signatureVerifier;
    @Mock
    private PaymentCallbackService callbackService;
    @Mock
    private IdempotencyService idempotencyService;

    private PaymentWebhookHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentWebhookHandler(signatureVerifier, callbackService, idempotencyService);
    }

    @Test
    @DisplayName("서명 검증 후 pgTransactionId 키로 멱등 처리하고 콜백 서비스에 위임한다")
    void dispatch_verifiesSignatureThenDelegates() {
        given(idempotencyService.execute(eq("payment-callback:mock-tx-1"), eq(PaymentCallbackResult.class), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        given(callbackService.applyResult(anyString(), any(), any()))
                .willReturn(new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 1L, "mock-tx-1", PaymentStatus.PAID));

        handler.dispatch(PAID_NOTIFICATION);

        verify(signatureVerifier).verify("secret");
        verify(callbackService).applyResult("mock-tx-1", PaymentStatus.PAID, null);
    }

    @Test
    @DisplayName("서명이 유효하지 않으면 멱등 처리·콜백 위임 없이 예외를 전파한다")
    void dispatch_invalidSignature_propagates() {
        willThrow(new InvalidWebhookSignatureException()).given(signatureVerifier).verify(any());

        assertThatThrownBy(() -> handler.dispatch(PAID_NOTIFICATION))
                .isInstanceOf(InvalidWebhookSignatureException.class);

        verifyNoInteractions(idempotencyService, callbackService);
    }
}
