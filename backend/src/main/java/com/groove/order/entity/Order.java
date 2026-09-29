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
			@Index(name = "idx_orders_status_expires", columnList = "status, expires_at"),
			@Index(name = "idx_orders_created", columnList = "created_at"),
			@Index(name = "idx_orders_status_created", columnList = "status, created_at"),
			@Index(name = "idx_orders_member_placed", columnList = "member_id, placed_at")
		})
public class Order extends BaseTimeEntity {

	public static final int PENDING_EXPIRATION_MINUTES = 10;
	public static final String EXPIRED_CANCEL_REASON = "EXPIRED";
	public static final String SUPERSEDED_CANCEL_REASON = "SUPERSEDED";

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

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "order_source", nullable = false, length = 10)
	@ColumnDefault("'CART'")
	private OrderSource orderSource;

	@Column(name = "placed_at")
	private LocalDateTime placedAt;

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
	private Order(String orderNumber, Member member, ShippingAddress shippingAddress, OrderSource orderSource,
			LocalDateTime now) {
		this.orderNumber = orderNumber;
		this.member = member;
		this.shippingAddress = shippingAddress;
		this.status = OrderStatus.PENDING;
		this.orderSource = orderSource;
		this.expiresAt = now.plusMinutes(PENDING_EXPIRATION_MINUTES);
		this.totalAmount = BigDecimal.ZERO;
		this.discountAmount = BigDecimal.ZERO;
		this.finalAmount = BigDecimal.ZERO;
	}

	public static Order create(String orderNumber, Member member, ShippingAddress shippingAddress,
			OrderSource orderSource, LocalDateTime now) {
		return Order.builder()
				.orderNumber(orderNumber)
				.member(member)
				.shippingAddress(shippingAddress)
				.orderSource(orderSource)
				.now(now)
				.build();
	}

	public void addItem(Product product, int quantity) {
		if (this.memberCoupon != null) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (quantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.items.add(OrderItem.of(this, product, quantity, nextProductOrderNumber()));
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
		DiscountAllocator.allocate(this.items, this.discountAmount);
	}

	private String nextProductOrderNumber() {
		return this.orderNumber + "-" + String.format("%02d", this.items.size() + 1);
	}

	public String getCouponName() {
		return this.memberCoupon == null ? null : this.memberCoupon.getCoupon().getName();
	}

	public void markPaid() {
		if (this.status == OrderStatus.PENDING) {
			this.status = OrderStatus.PAID;
			this.items.forEach(OrderItem::markPaid);
			return;
		}
		if (this.status == OrderStatus.PAID) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
		}
		throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
	}

	/** 가상계좌 발급으로 입금 대기 상태가 됐음을 상품주문에도 반영한다. 주문 상태(PENDING)는 그대로 둔다. */
	public void awaitDeposit() {
		if (this.status != OrderStatus.PENDING) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		this.items.forEach(OrderItem::awaitDeposit);
	}

	public void cancel(String reason) {
		if (this.status != OrderStatus.PENDING) {
			throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL);
		}
		this.status = OrderStatus.CANCELED;
		this.canceledAt = LocalDateTime.now();
		this.cancelReason = reason;
		LocalDateTime now = this.canceledAt;
		this.items.forEach(item -> item.cancel(now));
	}

	public void requestCancel(String reason, boolean byAdmin) {
		if (byAdmin) {
			if (!this.status.canTransitionTo(OrderStatus.CANCELED)) {
				throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
			}
			this.cancelReason = ADMIN_CANCEL_REASON;
			return;
		}
		if (this.status != OrderStatus.PAID) {
			throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL);
		}
		this.cancelReason = reason;
	}

	public void completeCancel(LocalDateTime now) {
		if (this.status != OrderStatus.PAID && this.status != OrderStatus.PREPARING) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		this.status = OrderStatus.CANCELED;
		this.canceledAt = now;
		this.items.forEach(item -> item.cancel(now));
	}

	public void withdrawCancelRequest() {
		this.cancelReason = null;
	}

	/** 관리자 상태 전이(PATCH /admin/orders/{id}/status)용. 허용되지 않는 전이는 예외를 던진다. */
	public void changeStatus(OrderStatus next) {
		if (!this.status.canTransitionTo(next)) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
		}
		this.status = next;
		LocalDateTime now = LocalDateTime.now();
		switch (next) {
			case PREPARING -> this.items.forEach(item -> item.moveToPreparing(now));
			case SHIPPED -> this.items.forEach(item -> item.moveToShipping(now));
			case DELIVERED -> this.items.forEach(item -> item.moveToDelivered(now));
			case CANCELED -> {
				this.canceledAt = now;
				this.cancelReason = ADMIN_CANCEL_REASON;
				this.items.forEach(item -> item.cancel(now));
			}
			default -> {
			}
		}
	}

	/** 가상계좌 발급 시 입금기한으로 만료를 늘린다. PENDING 이 아니거나 기존 기한보다 이르면 무시한다. */
	public void extendExpiry(LocalDateTime dueDate) {
		if (this.status != OrderStatus.PENDING) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		if (dueDate != null && dueDate.isAfter(this.expiresAt)) {
			this.expiresAt = dueDate;
		}
	}

	public boolean isExpired(LocalDateTime now) {
		return this.status == OrderStatus.PENDING && !now.isBefore(this.expiresAt);
	}

	/** 결제 승인이나 가상계좌 발급으로 주문을 확정한다. 이미 확정된 주문은 최초 확정 시각을 유지한다(멱등). */
	public void place(LocalDateTime at) {
		if (this.placedAt == null) {
			this.placedAt = at;
		}
	}

	public boolean isPlaced() {
		return this.placedAt != null;
	}

	/**
	 * 스케줄러가 결제 기한이 지난 PENDING 주문을 취소할 때 쓴다. 상태값을 새로 두지 않고 CANCELED + 사유로 구분한다.
	 * 가상계좌 입금기한 만료(상품주문이 이미 PAYMENT_WAITING)는 CANCELED_BY_NOPAYMENT로, 그 외(10분 만료)는
	 * CANCELED로 상품주문을 옮긴다.
	 */
	public void expire(LocalDateTime now) {
		if (this.status != OrderStatus.PENDING) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		this.status = OrderStatus.CANCELED;
		this.canceledAt = now;
		this.cancelReason = EXPIRED_CANCEL_REASON;
		this.items.forEach(item -> {
			if (item.getStatus() == OrderItemStatus.PAYMENT_WAITING) {
				item.cancelByNoPayment(now);
			} else {
				item.cancel(now);
			}
		});
	}

	/** 주문서를 다시 제출해 이 주문이 필요 없어졌을 때 쓴다. 만료와 같은 복원을 하되 사유만 다르다. */
	public void supersede(LocalDateTime now) {
		if (this.status != OrderStatus.PENDING) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		this.status = OrderStatus.CANCELED;
		this.canceledAt = now;
		this.cancelReason = SUPERSEDED_CANCEL_REASON;
		this.items.forEach(item -> item.cancel(now));
	}

	/** 주문서 배송지만 바꾼다. 확정 전(placed_at 없음) PENDING 주문에만 허용한다. */
	public void changeShippingAddress(ShippingAddress shippingAddress) {
		if (this.status != OrderStatus.PENDING || isPlaced()) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
		}
		this.shippingAddress = shippingAddress;
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
