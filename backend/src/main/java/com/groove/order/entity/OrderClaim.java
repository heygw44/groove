package com.groove.order.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 취소·반품 클레임 건. 상품주문(order_item)에 걸리는 요청 1건이 행 1개다. {@link OrderItem#getClaimStatus()} 는
 * 이 테이블의 최신 진행 상태를 상품주문 행에 파생 표시하는 값이고, 이 엔티티가 클레임 건 자체의 이력(사유·시각·
 * 환불계좌)을 갖는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "order_claim",
		indexes = {
			@Index(name = "idx_order_claim_status_requested", columnList = "status, requested_at")
		})
public class OrderClaim extends BaseTimeEntity {

	private static final int MAX_REASON_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "order_item_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_order_claim_order_item"))
	private OrderItem orderItem;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private OrderClaimType type;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private OrderClaimStatus status;

	@Column(length = MAX_REASON_LENGTH)
	private String reason;

	@Column(name = "reject_reason", length = MAX_REASON_LENGTH)
	private String rejectReason;

	/** 반품 완료 처리 시 관리자가 고른 재입고 여부. CANCEL 은 항상 재고를 복원하므로 null 로 둔다. */
	private Boolean restock;

	@Column(name = "refund_account_bank_code", length = 10)
	private String refundAccountBankCode;

	@Column(name = "refund_account_number", length = 64)
	private String refundAccountNumber;

	@Column(name = "refund_account_holder_name", length = 100)
	private String refundAccountHolderName;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	/** 락 이전 스냅샷으로 판단한 쓰기가 다른 트랜잭션의 변경을 덮어쓰지 못하게 하는 최종 방어선. 충돌은 409. */
	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	private Long version;

	private OrderClaim(OrderItem orderItem, OrderClaimType type, String reason, RefundAccountInfo refundAccount,
			LocalDateTime requestedAt) {
		this.orderItem = orderItem;
		this.type = type;
		this.status = OrderClaimStatus.REQUESTED;
		this.reason = truncate(reason);
		this.requestedAt = requestedAt;
		applyRefundAccount(refundAccount);
	}

	public static OrderClaim requestCancel(OrderItem orderItem, String reason, RefundAccountInfo refundAccount,
			LocalDateTime now) {
		return new OrderClaim(orderItem, OrderClaimType.CANCEL, reason, refundAccount, now);
	}

	/** 가상계좌 결제면 수거 완료 시 환불에 쓸 계좌를 요청 시점에 함께 받는다. */
	public static OrderClaim requestReturn(OrderItem orderItem, String reason, RefundAccountInfo refundAccount,
			LocalDateTime now) {
		return new OrderClaim(orderItem, OrderClaimType.RETURN, reason, refundAccount, now);
	}

	/** 관리자 승인 또는 즉시 취소 가능 구간(PAID)의 자동 승인. CANCEL 클레임 전용. */
	public void approve(LocalDateTime now) {
		if (this.type != OrderClaimType.CANCEL || this.status != OrderClaimStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		this.status = OrderClaimStatus.DONE;
		this.resolvedAt = now;
		clearRefundAccount();
	}

	/** 반품 수거 시작. RETURN 클레임 전용. */
	public void startCollecting() {
		if (this.type != OrderClaimType.RETURN || this.status != OrderClaimStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		this.status = OrderClaimStatus.COLLECTING;
	}

	/**
	 * 반품 수거 완료 처리 시 관리자가 고른 재입고 여부를 미리 기록한다. 환불 결과가 불명해 완료 처리
	 * ({@link #complete}) 가 뒤로 미뤄져도 대사가 이 값을 그대로 읽어 마무리할 수 있게 한다.
	 */
	public void chooseRestock(Boolean restock) {
		if (this.type != OrderClaimType.RETURN || this.status != OrderClaimStatus.COLLECTING) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		this.restock = restock;
	}

	/** 반품 수거 완료(환불 포함). RETURN 클레임 전용. */
	public void complete(LocalDateTime now, Boolean restock) {
		if (this.type != OrderClaimType.RETURN || this.status != OrderClaimStatus.COLLECTING) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		this.status = OrderClaimStatus.DONE;
		this.resolvedAt = now;
		this.restock = restock;
		clearRefundAccount();
	}

	/** 관리자 거부. REQUESTED·COLLECTING 에서만 가능하다. */
	public void reject(String rejectReason, LocalDateTime now) {
		if (this.status != OrderClaimStatus.REQUESTED && this.status != OrderClaimStatus.COLLECTING) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		this.status = OrderClaimStatus.REJECTED;
		this.rejectReason = truncate(rejectReason);
		this.resolvedAt = now;
		clearRefundAccount();
	}

	/** 구매자 요청 철회. REQUESTED 에서만 가능하다(수거가 시작되면 철회할 수 없다). */
	public void withdraw(LocalDateTime now) {
		if (this.status != OrderClaimStatus.REQUESTED) {
			throw new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
		this.status = OrderClaimStatus.WITHDRAWN;
		this.resolvedAt = now;
		clearRefundAccount();
	}

	public boolean isInProgress() {
		return this.status == OrderClaimStatus.REQUESTED || this.status == OrderClaimStatus.COLLECTING;
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

	/** 처리가 끝나거나 거부되면 환불계좌를 지운다(D6). */
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
