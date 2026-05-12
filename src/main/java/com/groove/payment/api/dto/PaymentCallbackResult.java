package com.groove.payment.api.dto;

import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentStatus;

/**
 * 결제 결과 콜백 처리 결과 (#W7-4). {@code POST /api/v1/payments/webhook} 응답 본문이자 폴링 스케줄러의
 * 내부 결과 — {@code IdempotencyService} 가 JSON 으로 캐싱해 중복 콜백 시 그대로 replay 한다. 따라서 JSON
 * 왕복 가능한 단순 record 여야 한다.
 *
 * @param outcome         처리 결과 분기
 * @param paymentId       결제 식별자 ({@link Outcome#IGNORED} 면 {@code null})
 * @param pgTransactionId PG 거래 식별자
 * @param paymentStatus   처리 후 결제 상태 ({@link Outcome#IGNORED} 면 {@code null})
 */
public record PaymentCallbackResult(
        Outcome outcome,
        Long paymentId,
        String pgTransactionId,
        PaymentStatus paymentStatus) {

    public enum Outcome {
        /** 이번 콜백으로 PENDING → PAID/FAILED 전이를 적용했다. */
        APPLIED,
        /** 결제가 이미 종착 상태였다 — 중복 콜백, 무해하게 무시. */
        ALREADY_PROCESSED,
        /** 알 수 없는 거래 식별자 — 처리할 결제가 없어 무시. */
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
