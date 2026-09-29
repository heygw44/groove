package com.groove.order.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.product.entity.Product;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 항목(상품주문). 상품명·가격은 주문 시점 스냅샷으로 보관한다. 결제 생애주기({@link Order#getStatus()})와
 * 분리된 이행 상태({@link OrderItemStatus})를 갖고, Order 의 기존 전이 메서드가 이 상태도 같이 옮긴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "order_item",
		uniqueConstraints = @UniqueConstraint(name = "uk_order_item_product_order_number",
				columnNames = "product_order_number"),
		indexes = {
			@Index(name = "idx_order_item_order", columnList = "order_id"),
			@Index(name = "idx_order_item_product", columnList = "product_id"),
			@Index(name = "idx_order_item_status", columnList = "status, delivered_at")
		})
public class OrderItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
	private Order order;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_product"))
	private Product product;

	@Column(name = "product_name_snapshot", nullable = false, length = 200)
	private String productName;

	@Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
	private BigDecimal productPrice;

	@Column(nullable = false)
	private int quantity;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	@ColumnDefault("'PAYMENT_PENDING'")
	private OrderItemStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "claim_status", length = 20)
	private OrderItemClaimStatus claimStatus;

	@Column(name = "product_order_number", nullable = false, length = 40)
	private String productOrderNumber;

	@Column(name = "discount_share", nullable = false, precision = 10, scale = 2)
	@ColumnDefault("0")
	private BigDecimal discountShare;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "courier_code", length = 20)
	private CourierCode courierCode;

	@Column(name = "tracking_number", length = 50)
	private String trackingNumber;

	@Column(name = "prepared_at")
	private LocalDateTime preparedAt;

	@Column(name = "shipped_at")
	private LocalDateTime shippedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Builder(access = PRIVATE)
	private OrderItem(Order order, Product product, String productName, BigDecimal productPrice, int quantity,
			String productOrderNumber) {
		this.order = order;
		this.product = product;
		this.productName = productName;
		this.productPrice = productPrice;
		this.quantity = quantity;
		this.status = OrderItemStatus.PAYMENT_PENDING;
		this.productOrderNumber = productOrderNumber;
		this.discountShare = BigDecimal.ZERO;
	}

	static OrderItem of(Order order, Product product, int quantity, String productOrderNumber) {
		return OrderItem.builder()
				.order(order)
				.product(product)
				.productName(product.getTitle())
				.productPrice(product.getPrice())
				.quantity(quantity)
				.productOrderNumber(productOrderNumber)
				.build();
	}

	public BigDecimal getLineAmount() {
		return this.productPrice.multiply(BigDecimal.valueOf(this.quantity));
	}

	/** 취소·반품 시 환불할 금액. 라인 금액에서 쿠폰 할인 배분 몫을 뺀다(D5). */
	public BigDecimal getRefundableAmount() {
		return getLineAmount().subtract(this.discountShare);
	}

	/** 결제 승인(카드·간편결제 즉시 승인 또는 가상계좌 입금 확인)으로 결제가 끝났음을 반영한다. */
	void markPaid() {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.PAID;
	}

	/** 가상계좌 발급으로 입금 대기 상태로 넘어간다. */
	void awaitDeposit() {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.PAYMENT_WAITING;
	}

	/** 결제 전(만료·재제출) 또는 결제 후(구매자·관리자 취소) 취소를 반영한다. */
	void cancel(LocalDateTime now) {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.CANCELED;
		this.canceledAt = now;
	}

	/** 가상계좌 입금기한 만료로 취소됐음을 반영한다. */
	void cancelByNoPayment(LocalDateTime now) {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.CANCELED_BY_NOPAYMENT;
		this.canceledAt = now;
	}

	/** 관리자 발주확인. */
	void moveToPreparing(LocalDateTime now) {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.PREPARING;
		this.preparedAt = now;
	}

	/** 관리자 발송처리. */
	void moveToShipping(LocalDateTime now) {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.SHIPPING;
		this.shippedAt = now;
	}

	/** 배송완료(관리자 또는 자동). */
	void moveToDelivered(LocalDateTime now) {
		if (isTerminal()) {
			return;
		}
		this.status = OrderItemStatus.DELIVERED;
		this.deliveredAt = now;
	}

	/** 쿠폰 할인액을 라인 금액 비율로 나눈 몫을 반영한다. {@link DiscountAllocator} 에서만 호출한다. */
	void applyDiscountShare(BigDecimal discountShare) {
		this.discountShare = discountShare;
	}

	/** 환불 결과를 기다리는 동안(즉시 취소 포함) 또는 관리자 승인을 기다리는 동안 진행 중 클레임을 표시한다. */
	public void markClaimRequested(OrderItemClaimStatus claimStatus) {
		this.claimStatus = claimStatus;
	}

	/** 반품 수거가 시작됐음을 표시한다. */
	public void markCollecting() {
		this.claimStatus = OrderItemClaimStatus.COLLECTING;
	}

	/** 취소 클레임이 승인·완료돼 상품주문이 취소로 확정된다. */
	public void completeCancelClaim(LocalDateTime now) {
		this.status = OrderItemStatus.CANCELED;
		this.claimStatus = OrderItemClaimStatus.CANCEL_DONE;
		this.canceledAt = now;
	}

	/** 반품 클레임이 완료돼 상품주문이 반품으로 확정된다. */
	public void completeReturnClaim(LocalDateTime now) {
		this.status = OrderItemStatus.RETURNED;
		this.claimStatus = OrderItemClaimStatus.RETURN_DONE;
		this.canceledAt = now;
	}

	/** 클레임이 거부되거나(관리자, 토스 거절) 철회 없이 종결돼 이행 상태는 그대로 두고 파생 표시만 남긴다. */
	public void markClaimRejected(OrderItemClaimStatus rejectStatus) {
		this.claimStatus = rejectStatus;
	}

	/** 클레임을 철회해 진행 중 표시를 지운다. */
	public void clearClaim() {
		this.claimStatus = null;
	}

	public boolean isClaimInProgress() {
		return this.claimStatus == OrderItemClaimStatus.CANCEL_REQUEST
				|| this.claimStatus == OrderItemClaimStatus.RETURN_REQUEST
				|| this.claimStatus == OrderItemClaimStatus.COLLECTING;
	}

	public boolean isTerminal() {
		return this.status == OrderItemStatus.CANCELED
				|| this.status == OrderItemStatus.CANCELED_BY_NOPAYMENT
				|| this.status == OrderItemStatus.PURCHASE_CONFIRMED
				|| this.status == OrderItemStatus.RETURNED;
	}
}
