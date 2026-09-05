package com.groove.payment.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 결제. 주문 1건에 1건 대응하며 토스 승인/취소 결과를 기록한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "payment",
		uniqueConstraints = {
			@UniqueConstraint(name = "uk_payment_order", columnNames = "order_id"),
			@UniqueConstraint(name = "uk_payment_key", columnNames = "payment_key"),
			@UniqueConstraint(name = "uk_payment_toss_order_id", columnNames = "toss_order_id")
		},
		indexes = @Index(name = "idx_payment_approved_at", columnList = "approved_at"))
public class Payment extends BaseTimeEntity {

	private static final int MAX_FAIL_REASON_LENGTH = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_order"))
	private Order order;

	@Column(name = "payment_key", length = 200)
	private String paymentKey;

	/** 토스에 넘기는 문자열 orderId. 주문번호를 그대로 재사용한다. */
	@Column(name = "toss_order_id", nullable = false, length = 64)
	private String tossOrderId;

	@Column(length = 30)
	private String method;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'READY'")
	private PaymentStatus status;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Column(name = "fail_reason", length = MAX_FAIL_REASON_LENGTH)
	private String failReason;

	private Payment(Order order) {
		this.order = order;
		this.tossOrderId = order.getOrderNumber();
		this.amount = order.getFinalAmount();
		this.status = PaymentStatus.READY;
	}

	public static Payment ready(Order order) {
		return new Payment(order);
	}

	/** FAILED 에서도 승인을 허용한다. 승인에 실패한 주문은 PENDING 으로 남아 다시 결제할 수 있어야 한다. */
	public void approve(String key, String payMethod, LocalDateTime approvedTime) {
		validateApprovable();
		this.paymentKey = key;
		this.method = payMethod;
		this.approvedAt = approvedTime;
		this.failReason = null;
		this.status = PaymentStatus.DONE;
	}

	public void fail(String reason) {
		validateApprovable();
		this.failReason = truncate(reason);
		this.status = PaymentStatus.FAILED;
	}

	public void cancel(LocalDateTime canceledTime) {
		if (this.status != PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.canceledAt = canceledTime;
		this.status = PaymentStatus.CANCELED;
	}

	private void validateApprovable() {
		if (this.status == PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_DONE);
		}
		if (this.status == PaymentStatus.CANCELED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
	}

	private String truncate(String reason) {
		if (reason == null || reason.length() <= MAX_FAIL_REASON_LENGTH) {
			return reason;
		}
		return reason.substring(0, MAX_FAIL_REASON_LENGTH);
	}
}
