package com.groove.payment.api.dto;

import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 결제 결과 콜백 처리 결과. JSON 으로 캐싱돼 중복 콜백 시 replay 되는 단순 record.
 */
public record PaymentCallbackResult(
        @Schema(description = "콜백 처리 결과 분기", example = "APPLIED")
        Outcome outcome,
        @Schema(description = "결제 식별자 — IGNORED 면 null", example = "1")
        Long paymentId,
        @Schema(description = "PG 거래 식별자", example = "pg-tx-20260101-0001")
        String pgTransactionId,
        @Schema(description = "처리 후 결제 상태 — IGNORED 면 null", example = "PAID")
        PaymentStatus paymentStatus) {

    public enum Outcome {
        /** PENDING → PAID/FAILED 전이를 적용했다. */
        APPLIED,
        /** 결제가 이미 종착 상태라 무시. */
        ALREADY_PROCESSED,
        /** 알 수 없는 거래 식별자라 무시. */
        IGNORED
    }

    public static PaymentCallbackResult applied(Payment payment) {
        return new PaymentCallbackResult(Outcome.APPLIED, payment.getId(), payment.getPgTransactionId(), payment.getStatus());
    }

    public static PaymentCallbackResult alreadyProcessed(Payment payment) {
        return new PaymentCallbackResult(Outcome.ALREADY_PROCESSED, payment.getId(), payment.getPgTransactionId(), payment.getStatus());
    }

    public static PaymentCallbackResult ignored(String pgTransactionId) {
        return new PaymentCallbackResult(Outcome.IGNORED, null, pgTransactionId, null);
    }
}
