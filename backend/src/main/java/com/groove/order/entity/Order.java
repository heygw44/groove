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

import com.groove.coupon.entity.MemberCoupon;
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

/** 주문. 쿠폰을 적용하면 해당 MemberCoupon 을 참조하고 할인 금액을 반영한다. */
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

	private static final String ADMIN_CANCEL_REASON = "관리자 취소";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false, length = 30)
	private String orderNumber;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_member"))
	private Member member;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "member_coupon_id", foreignKey = @ForeignKey(name = "fk_orders_member_coupon"))
	private MemberCoupon memberCoupon;

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
		if (this.memberCoupon != null) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (quantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.items.add(OrderItem.of(this, product, quantity));
		calculateAmounts();
	}

	public void applyCoupon(MemberCoupon memberCoupon, BigDecimal discountAmount) {
		if (this.items.isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0
				|| discountAmount.compareTo(this.totalAmount) > 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.memberCoupon = memberCoupon;
		this.discountAmount = discountAmount;
		calculateAmounts();
	}

	public String getCouponName() {
		return this.memberCoupon == null ? null : this.memberCoupon.getCoupon().getName();
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

	/** 관리자 상태 전이(PATCH /admin/orders/{id}/status)용. 허용되지 않는 전이는 예외를 던진다. */
	public void changeStatus(OrderStatus next) {
		if (!this.status.canTransitionTo(next)) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
		}
		this.status = next;
		if (next == OrderStatus.CANCELED) {
			this.canceledAt = LocalDateTime.now();
			this.cancelReason = ADMIN_CANCEL_REASON;
		}
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
