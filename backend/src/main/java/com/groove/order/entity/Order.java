package com.groove.order.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.product.entity.Product;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문. member_coupon_id 는 MemberCoupon 엔티티(5주차 예정) 도입 전까지 FK 없이 단순 컬럼으로만 둔다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "orders",
		uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
		indexes = {
			@Index(name = "idx_orders_member_created", columnList = "member_id, created_at"),
			@Index(name = "idx_orders_status", columnList = "status")
		})
public class Order extends BaseTimeEntity {

	public static final int PENDING_EXPIRATION_MINUTES = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, length = 30)
	private String orderNumber;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_member"))
	private Member member;

	@Column(name = "member_coupon_id")
	private Long memberCouponId;

	@Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
	@ColumnDefault("0")
	private BigDecimal discountAmount;

	@Column(name = "final_amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal finalAmount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'PENDING'")
	private OrderStatus status;

	@Embedded
	private ShippingAddress shippingAddress;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;

	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderItem> items = new ArrayList<>();

	@Builder(access = PRIVATE)
	private Order(String orderNumber, Member member, ShippingAddress shippingAddress) {
		this.orderNumber = orderNumber;
		this.member = member;
		this.shippingAddress = shippingAddress;
		this.status = OrderStatus.PENDING;
		this.expiresAt = LocalDateTime.now().plusMinutes(PENDING_EXPIRATION_MINUTES);
		this.totalAmount = BigDecimal.ZERO;
		this.discountAmount = BigDecimal.ZERO;
		this.finalAmount = BigDecimal.ZERO;
	}

	public static Order create(String orderNumber, Member member, ShippingAddress shippingAddress) {
		return Order.builder()
				.orderNumber(orderNumber)
				.member(member)
				.shippingAddress(shippingAddress)
				.build();
	}

	public void addItem(Product product, int quantity) {
		if (quantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.items.add(OrderItem.of(this, product, quantity));
		calculateAmounts();
	}

	public void markPaid() {
		if (this.status == OrderStatus.PENDING) {
			this.status = OrderStatus.PAID;
			return;
		}
		if (this.status == OrderStatus.PAID) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
		}
		throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
	}

	public void cancel(String reason) {
		if (!this.status.isCancelable()) {
			throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL);
		}
		this.status = OrderStatus.CANCELED;
		this.canceledAt = LocalDateTime.now();
		this.cancelReason = reason;
	}

	public List<OrderItem> getItems() {
		return Collections.unmodifiableList(this.items);
	}

	private void calculateAmounts() {
		this.totalAmount = this.items.stream()
				.map(OrderItem::getLineAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		this.finalAmount = this.totalAmount.subtract(this.discountAmount);
	}
}
