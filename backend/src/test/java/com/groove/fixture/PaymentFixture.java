package com.groove.fixture;

import java.time.LocalDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.order.entity.Order;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

public final class PaymentFixture {

	public static final String PAYMENT_KEY = "tviva20260902abcdef";
	public static final String METHOD = "카드";
	public static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 2, 10, 1, 12);
	public static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 9, 2, 11, 32, 4);

	private PaymentFixture() {
	}

	public static Payment approved(Order order) {
		Payment payment = Payment.ready(order);
		payment.approve(PAYMENT_KEY, METHOD, APPROVED_AT);
		return payment;
	}

	public static Payment failed(Order order, String reason) {
		Payment payment = Payment.ready(order);
		payment.fail(reason);
		return payment;
	}

	public static Payment canceled(Order order) {
		Payment payment = approved(order);
		payment.cancel(CANCELED_AT);
		return payment;
	}

	public static Payment withStatus(Payment payment, PaymentStatus status) {
		ReflectionTestUtils.setField(payment, "status", status);
		return payment;
	}
}
