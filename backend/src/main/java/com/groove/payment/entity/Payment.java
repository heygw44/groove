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
import jakarta.persistence.Version;
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
		indexes = {
			@Index(name = "idx_payment_approved_at", columnList = "approved_at"),
			@Index(name = "idx_payment_canceled_at", columnList = "canceled_at"),
			@Index(name = "idx_payment_status_updated", columnList = "status, updated_at")
		})
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

	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	private Long version;

	@Column(name = "reconcile_attempts", nullable = false)
	@ColumnDefault("0")
	private int reconcileAttempts;

	@Column(name = "easy_pay_provider", length = 30)
	private String easyPayProvider;

	@Column(name = "va_bank_code", length = 10)
	private String vaBankCode;

	@Column(name = "va_account_number", length = 64)
	private String vaAccountNumber;

	@Column(name = "va_customer_name", length = 100)
	private String vaCustomerName;

	@Column(name = "va_due_date")
	private LocalDateTime vaDueDate;

	/** 입금 웹훅의 secret 상수시간 비교용 SHA-256 해시. 평문 secret 은 저장하지 않는다. */
	@Column(name = "va_secret_hash", length = 64)
	private String vaSecretHash;

	private Payment(Order order) {
		this.order = order;
		this.tossOrderId = order.getOrderNumber();
		this.amount = order.getFinalAmount();
		this.status = PaymentStatus.READY;
	}

	public static Payment ready(Order order) {
		return new Payment(order);
	}

	/**
	 * FAILED 에서도 승인을 허용한다. 승인에 실패한 주문은 PENDING 으로 남아 다시 결제할 수 있어야 한다.
	 * UNKNOWN 에서도 승인을 허용한다. 재시도나 대사 과정에서 실제로는 승인된 결제였음이 뒤늦게 확인될 수 있다.
	 */
	public void approve(String key, String payMethod, LocalDateTime approvedTime) {
		approve(key, payMethod, approvedTime, null);
	}

	/** 카드·간편결제 승인. 간편결제가 아니면 easyPayProvider 는 null 이다. */
	public void approve(String key, String payMethod, LocalDateTime approvedTime, String easyPayProvider) {
		validateApprovable();
		this.paymentKey = key;
		this.method = payMethod;
		this.approvedAt = approvedTime;
		this.failReason = null;
		this.easyPayProvider = easyPayProvider;
		this.status = PaymentStatus.DONE;
	}

	/** 가상계좌 발급. 입금 전까지 WAITING_FOR_DEPOSIT 로 남고, 입금 확인은 approve() 로 DONE 전이한다. */
	public void issueVirtualAccount(String key, String payMethod, String bankCode, String accountNumber,
			String customerName, LocalDateTime dueDate, String secretHash) {
		validateApprovable();
		this.paymentKey = key;
		this.method = payMethod;
		this.failReason = null;
		this.vaBankCode = bankCode;
		this.vaAccountNumber = accountNumber;
		this.vaCustomerName = customerName;
		this.vaDueDate = dueDate;
		this.vaSecretHash = secretHash;
		this.status = PaymentStatus.WAITING_FOR_DEPOSIT;
	}

	public boolean isVirtualAccount() {
		return this.vaBankCode != null;
	}

	/**
	 * 입금 전 가상계좌를 닫을 때 쓴다(사용자 취소·입금기한 만료). 한 번도 승인된 적이 없어 approvedAt 은
	 * 비워 둔다 - 매출 집계는 DONE/CANCELED 의 approved_at 으로 날짜를 잡으므로, 비워두면 애초에 매출로
	 * 잡히지 않았던 결제가 취소 집계에도 섞이지 않는다.
	 */
	public void cancelVirtualAccount(String reason, LocalDateTime canceledTime) {
		if (this.status != PaymentStatus.WAITING_FOR_DEPOSIT) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.canceledAt = canceledTime;
		this.failReason = truncate(reason);
		this.status = PaymentStatus.CANCELED;
	}

	public void fail(String reason) {
		validateApprovable();
		this.failReason = truncate(reason);
		this.status = PaymentStatus.FAILED;
	}

	/** 토스 승인 호출이 timeout/5xx 로 결과를 알 수 없을 때 호출한다. 이후 대사나 재조회로 DONE/FAILED 로 수렴시킨다. */
	public void markUnknown(String reason) {
		validateApprovable();
		this.failReason = truncate(reason);
		this.status = PaymentStatus.UNKNOWN;
	}

	public void requestCancel() {
		if (this.status != PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.status = PaymentStatus.CANCEL_REQUESTED;
	}

	public void completeCancel(LocalDateTime canceledTime) {
		if (this.status != PaymentStatus.CANCEL_REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.canceledAt = canceledTime;
		this.status = PaymentStatus.CANCELED;
	}

	public void revertCancelRequest() {
		if (this.status != PaymentStatus.CANCEL_REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.status = PaymentStatus.DONE;
	}

	/** FAILED 로 남아 대사 대상에서 빠진 결제를 재시도용 READY 로 되돌린다. updated_at 이 갱신돼 grace 도 새로 시작한다. */
	public void retry() {
		if (this.status != PaymentStatus.FAILED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.failReason = null;
		this.reconcileAttempts = 0;
		this.status = PaymentStatus.READY;
	}

	/**
	 * 토스가 승인한 결제를 뒤늦게 취소로 수렴시킨다(승인 후 주문 무효, 대사 결과 토스에서 이미 취소됨 등).
	 * approvedAt 을 채우는 이유: 매출 집계는 DONE/CANCELED 의 approved_at 을 매출로, CANCELED 의
	 * canceled_at 을 취소로 센다. approvedAt 을 비우면 취소 금액만 늘어 순매출이 실제보다 줄어든다.
	 */
	public void compensate(String key, LocalDateTime approvedTime, LocalDateTime canceledTime, String reason) {
		if (this.status == PaymentStatus.CANCELED) {
			return;
		}
		if (this.status == PaymentStatus.DONE || this.status == PaymentStatus.CANCEL_REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		if (this.paymentKey == null) {
			this.paymentKey = key;
		}
		if (this.approvedAt == null) {
			this.approvedAt = approvedTime != null ? approvedTime : canceledTime;
		}
		this.canceledAt = canceledTime;
		this.failReason = truncate(reason);
		this.status = PaymentStatus.CANCELED;
	}

	public void recordReconcileMiss() {
		this.reconcileAttempts++;
	}

	/** approve/fail/markUnknown 공통 전이 검증. READY·FAILED·UNKNOWN 에서만 다음 상태로 넘어갈 수 있다. */
	private void validateApprovable() {
		if (this.status == PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_ALREADY_DONE);
		}
		if (this.status == PaymentStatus.CANCELED || this.status == PaymentStatus.CANCEL_REQUESTED) {
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
