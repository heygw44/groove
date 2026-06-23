package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentSettlementService 단위 테스트")
class PaymentSettlementServiceTest {

    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private PaymentCallbackService callbackService;

    @InjectMocks
    private PaymentSettlementService settlementService;

    @Test
    @DisplayName("공유 멱등키(payment-callback:{pgTx})로 applyResult 를 위임한다")
    void settle_delegatesViaSharedKey() {
        given(idempotencyService.execute(anyString(), eq(PaymentCallbackResult.class), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        PaymentCallbackResult applied = new PaymentCallbackResult(
                PaymentCallbackResult.Outcome.APPLIED, 1L, "pg-tx-1", PaymentStatus.PAID);
        given(callbackService.applyResult("pg-tx-1", PaymentStatus.PAID, null)).willReturn(applied);

        PaymentCallbackResult result = settlementService.settle("pg-tx-1", PaymentStatus.PAID, null);

        verify(idempotencyService).execute(eq("payment-callback:pg-tx-1"), eq(PaymentCallbackResult.class), any());
        verify(callbackService).applyResult("pg-tx-1", PaymentStatus.PAID, null);
        assertThat(result).isEqualTo(applied);
    }

    @Test
    @DisplayName("FAILED 사유를 그대로 applyResult 에 전달한다")
    void settle_passesFailureReason() {
        given(idempotencyService.execute(anyString(), eq(PaymentCallbackResult.class), any()))
                .willAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
        given(callbackService.applyResult(anyString(), any(), any())).willReturn(
                new PaymentCallbackResult(PaymentCallbackResult.Outcome.APPLIED, 1L, "pg-tx-2", PaymentStatus.FAILED));

        settlementService.settle("pg-tx-2", PaymentStatus.FAILED, "사유");

        verify(callbackService).applyResult("pg-tx-2", PaymentStatus.FAILED, "사유");
    }
}
