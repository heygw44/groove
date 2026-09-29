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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 취소 건 하나. 토스 부분취소는 건마다 다른 Idempotency-Key 가 필요해 취소 요청 자체를 이력으로 남긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "payment_cancel",
		uniqueConstraints = @UniqueConstraint(name = "uk_payment_cancel_idempotency_key",
				columnNames = "idempotency_key"),
		indexes = @Index(name = "idx_payment_cancel_payment_status", columnList = "payment_id, status"))
public class PaymentCancel extends BaseTimeEntity {

	private static final int MAX_REASON_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "payment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_cancel_payment"))
	private Payment payment;

	@Column(name = "idempotency_key", nullable = false, length = 300)
	private String idempotencyKey;

	@Column(name = "cancel_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal cancelAmount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'REQUESTED'")
	private PaymentCancelStatus status;

	@Column(name = "toss_transaction_key", length = 200)
	private String tossTransactionKey;

	@Column(length = MAX_REASON_LENGTH)
	private String reason;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "done_at")
	private LocalDateTime doneAt;

	private PaymentCancel(Payment payment, String idempotencyKey, BigDecimal cancelAmount, String reason,
			LocalDateTime requestedAt) {
		this.payment = payment;
		this.idempotencyKey = idempotencyKey;
		this.cancelAmount = cancelAmount;
		this.reason = truncate(reason);
		this.status = PaymentCancelStatus.REQUESTED;
		this.requestedAt = requestedAt;
	}

	public static PaymentCancel request(Payment payment, String idempotencyKey, BigDecimal cancelAmount,
			String reason, LocalDateTime requestedAt) {
		return new PaymentCancel(payment, idempotencyKey, cancelAmount, reason, requestedAt);
	}

	public void complete(String transactionKey, LocalDateTime doneTime) {
		if (this.status != PaymentCancelStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.tossTransactionKey = transactionKey;
		this.doneAt = doneTime;
		this.status = PaymentCancelStatus.DONE;
	}

	public void fail() {
		if (this.status != PaymentCancelStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.status = PaymentCancelStatus.FAILED;
	}

	private String truncate(String value) {
		if (value == null || value.length() <= MAX_REASON_LENGTH) {
			return value;
		}
		return value.substring(0, MAX_REASON_LENGTH);
	}
}
