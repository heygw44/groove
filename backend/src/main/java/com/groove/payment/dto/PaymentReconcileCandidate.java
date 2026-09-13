package com.groove.payment.dto;

/** 대사 후보 결제 한 건. */
public record PaymentReconcileCandidate(Long paymentId, Long orderId, String tossOrderId) {
}
