package com.groove.payment.dto;

/** 보상 대기 큐 회수 후보 한 건. */
public record PaymentCompensationCandidate(String paymentKey, String reason) {
}
