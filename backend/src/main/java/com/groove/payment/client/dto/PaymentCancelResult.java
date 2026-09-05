package com.groove.payment.client.dto;

import java.time.LocalDateTime;

/** 결제 취소 결과. */
public record PaymentCancelResult(String paymentKey, String status, LocalDateTime canceledAt) {
}
