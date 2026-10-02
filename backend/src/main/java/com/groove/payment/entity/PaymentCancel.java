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
import com.groove.payment.client.dto.RefundAccountInfo;

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

	/**
	 * 이 취소 건이 상품 단위 취소·반품 클레임(order_claim) 승인으로 시작됐으면 그 id 를 담는다. 전액취소 등
	 * 클레임 없이 시작된 취소는 null 이다. 결과불명으로 남은 건을 대사({@code PaymentCancelRetrier})가 나중에
	 * 확정할 때 이 id 로 대상 클레임·상품주문을 찾아 마무리한다 - order 엔티티를 직접 참조하지 않고 대리키만
	 * 갖는 이유는 payment 패키지가 order.entity.OrderClaim 에 의존하지 않게 하기 위해서다.
	 */
	@Column(name = "order_claim_id")
	private Long orderClaimId;

	/** 가상계좌 전액취소를 대사가 같은 계좌로 다시 보낼 수 있게 요청 시점에 보관한다. 확정·실패하면 비운다. */
	@Column(name = "refund_account_bank_code", length = 10)
	private String refundAccountBankCode;

	@Column(name = "refund_account_number", length = 64)
	private String refundAccountNumber;

	@Column(name = "refund_account_holder_name", length = 100)
	private String refundAccountHolderName;

	private PaymentCancel(Payment payment, String idempotencyKey, BigDecimal cancelAmount, String reason,
			LocalDateTime requestedAt, Long orderClaimId) {
		this.payment = payment;
		this.idempotencyKey = idempotencyKey;
		this.cancelAmount = cancelAmount;
		this.reason = truncate(reason);
		this.status = PaymentCancelStatus.REQUESTED;
		this.requestedAt = requestedAt;
		this.orderClaimId = orderClaimId;
	}

	public static PaymentCancel request(Payment payment, String idempotencyKey, BigDecimal cancelAmount,
			String reason, LocalDateTime requestedAt) {
		return request(payment, idempotencyKey, cancelAmount, reason, requestedAt, null);
	}

	public static PaymentCancel request(Payment payment, String idempotencyKey, BigDecimal cancelAmount,
			String reason, LocalDateTime requestedAt, RefundAccountInfo refundAccount) {
		PaymentCancel paymentCancel = new PaymentCancel(payment, idempotencyKey, cancelAmount, reason, requestedAt,
				null);
		paymentCancel.applyRefundAccount(refundAccount);
		return paymentCancel;
	}

	public static PaymentCancel requestForClaim(Payment payment, String idempotencyKey, BigDecimal cancelAmount,
			String reason, LocalDateTime requestedAt, Long orderClaimId) {
		return new PaymentCancel(payment, idempotencyKey, cancelAmount, reason, requestedAt, orderClaimId);
	}

	public void complete(String transactionKey, LocalDateTime doneTime) {
		if (this.status != PaymentCancelStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.tossTransactionKey = transactionKey;
		this.doneAt = doneTime;
		this.status = PaymentCancelStatus.DONE;
		clearRefundAccount();
	}

	public void fail() {
		if (this.status != PaymentCancelStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.status = PaymentCancelStatus.FAILED;
		clearRefundAccount();
	}

	public RefundAccountInfo getRefundAccount() {
		if (this.refundAccountBankCode == null) {
			return null;
		}
		return new RefundAccountInfo(this.refundAccountBankCode, this.refundAccountNumber,
				this.refundAccountHolderName);
	}

	private void applyRefundAccount(RefundAccountInfo refundAccount) {
		if (refundAccount == null) {
			return;
		}
		this.refundAccountBankCode = refundAccount.bankCode();
		this.refundAccountNumber = refundAccount.accountNumber();
		this.refundAccountHolderName = refundAccount.holderName();
	}

	private void clearRefundAccount() {
		this.refundAccountBankCode = null;
		this.refundAccountNumber = null;
		this.refundAccountHolderName = null;
	}

	private String truncate(String value) {
		if (value == null || value.length() <= MAX_REASON_LENGTH) {
			return value;
		}
		return value.substring(0, MAX_REASON_LENGTH);
	}
}
