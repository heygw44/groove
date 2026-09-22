package com.groove.payment.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * payment 행이 없는 보상 취소 대기 큐. 같은 주문에 다른 paymentKey 로 동시에 승인이 들어와 나중에
 * 커밋된 쪽을 보상 취소할 때, 그 키는 uk_payment_order 때문에 payment 행을 가질 수 없다. 토스 취소를 부르기
 * 전에 이 행부터 커밋해, 취소 자체가 실패해도 대사 스케줄러가 이어받을 흔적을 남긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "payment_compensation",
		uniqueConstraints = @UniqueConstraint(name = "uk_payment_compensation_payment_key",
				columnNames = "payment_key"),
		indexes = @Index(name = "idx_payment_compensation_status_updated", columnList = "status, updated_at"))
public class PaymentCompensation extends BaseTimeEntity {

	private static final int MAX_REASON_LENGTH = 200;
	private static final int MAX_LAST_ERROR_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "payment_key", nullable = false, length = 200)
	private String paymentKey;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "order_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_payment_compensation_order"))
	private Order order;

	@Column(name = "toss_order_id", length = 64)
	private String tossOrderId;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(length = MAX_REASON_LENGTH)
	private String reason;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PaymentCompensationStatus status;

	@Column(nullable = false)
	@ColumnDefault("0")
	private int attempts;

	@Column(name = "last_error", length = MAX_LAST_ERROR_LENGTH)
	private String lastError;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	private PaymentCompensation(String paymentKey, Order order, String tossOrderId, LocalDateTime approvedAt,
			String reason) {
		this.paymentKey = paymentKey;
		this.order = order;
		this.tossOrderId = tossOrderId;
		this.approvedAt = approvedAt;
		this.reason = truncate(reason, MAX_REASON_LENGTH);
		this.status = PaymentCompensationStatus.PENDING;
	}

	public static PaymentCompensation pending(String paymentKey, Order order, String tossOrderId,
			LocalDateTime approvedAt, String reason) {
		return new PaymentCompensation(paymentKey, order, tossOrderId, approvedAt, reason);
	}

	/** 이미 취소됨이면 재시도가 뒤늦게 도착해도 그대로 둔다(멱등). */
	public void markCanceled(LocalDateTime canceledTime) {
		if (this.status == PaymentCompensationStatus.CANCELED) {
			return;
		}
		this.canceledAt = canceledTime;
		this.status = PaymentCompensationStatus.CANCELED;
	}

	public void recordFailure(String error) {
		this.attempts++;
		this.lastError = truncate(error, MAX_LAST_ERROR_LENGTH);
	}

	public void markManualReview() {
		this.status = PaymentCompensationStatus.MANUAL_REVIEW;
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}
}
