package com.groove.payment.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대사 스케줄러가 결제 한 건에 내린 판단과 결과 이력. 사람이 나중에 추적할 수 있도록 남긴다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "payment_reconcile_log",
		indexes = @Index(name = "idx_payment_reconcile_log_payment", columnList = "payment_id, created_at"))
public class PaymentReconcileLog extends BaseTimeEntity {

	private static final int MAX_DETAIL_LENGTH = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "payment_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_payment_reconcile_log_payment"))
	private Payment payment;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "before_status", nullable = false, length = 20)
	private PaymentStatus beforeStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "after_status", nullable = false, length = 20)
	private PaymentStatus afterStatus;

	@Column(name = "toss_status", length = 30)
	private String tossStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PaymentReconcileAction action;

	@Column(length = MAX_DETAIL_LENGTH)
	private String detail;

	private PaymentReconcileLog(Payment payment, PaymentStatus beforeStatus, String tossStatus,
			PaymentReconcileAction action, String detail) {
		this.payment = payment;
		this.beforeStatus = beforeStatus;
		this.afterStatus = payment.getStatus();
		this.tossStatus = tossStatus;
		this.action = action;
		this.detail = truncate(detail);
	}

	public static PaymentReconcileLog of(Payment payment, PaymentStatus beforeStatus, String tossStatus,
			PaymentReconcileAction action, String detail) {
		return new PaymentReconcileLog(payment, beforeStatus, tossStatus, action, detail);
	}

	private static String truncate(String value) {
		if (value == null || value.length() <= MAX_DETAIL_LENGTH) {
			return value;
		}
		return value.substring(0, MAX_DETAIL_LENGTH);
	}
}
