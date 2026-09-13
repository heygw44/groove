package com.groove.payment.dto;

import com.groove.payment.entity.PaymentStatus;

public record PaymentCancelTarget(Long orderId, PaymentStatus status) {
}
