package com.groove.fixture;

import java.math.BigDecimal;
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
		return approved(order, PAYMENT_KEY);
	}

	public static Payment approved(Order order, String paymentKey) {
		return approvedAt(order, paymentKey, APPROVED_AT);
	}

	public static Payment approvedAt(Order order, String paymentKey, LocalDateTime approvedAt) {
		Payment payment = Payment.ready(order);
		payment.approve(paymentKey, METHOD, approvedAt);
		return payment;
	}

	/** 입금이 확인돼 DONE 이 된 가상계좌 결제. */
	public static Payment virtualAccountApproved(Order order, String paymentKey) {
		Payment payment = Payment.ready(order);
		payment.issueVirtualAccount(paymentKey, "가상계좌", "088", "9999912345678", "홍길동",
				APPROVED_AT.plusDays(7), "secret-hash");
		payment.approve(paymentKey, "가상계좌", APPROVED_AT);
		return payment;
	}

	public static Payment canceledAt(Order order, String paymentKey, LocalDateTime approvedAt,
			LocalDateTime canceledAt) {
		Payment payment = approvedAt(order, paymentKey, approvedAt);
		payment.requestCancel();
		payment.completeCancel(canceledAt);
		return payment;
	}

	public static Payment partialCanceled(Order order, String paymentKey, LocalDateTime approvedAt,
			LocalDateTime canceledAt, BigDecimal cancelAmount) {
		Payment payment = approvedAt(order, paymentKey, approvedAt);
		payment.applyPartialCancel(cancelAmount, canceledAt);
		return payment;
	}

	public static Payment failed(Order order, String reason) {
		Payment payment = Payment.ready(order);
		payment.fail(reason);
		return payment;
	}

	public static Payment unknown(Order order, String reason) {
		Payment payment = Payment.ready(order);
		payment.markUnknown(reason);
		return payment;
	}

	public static Payment canceled(Order order) {
		Payment payment = approved(order);
		payment.requestCancel();
		payment.completeCancel(CANCELED_AT);
		return payment;
	}

	public static Payment withStatus(Payment payment, PaymentStatus status) {
		ReflectionTestUtils.setField(payment, "status", status);
		return payment;
	}
}
