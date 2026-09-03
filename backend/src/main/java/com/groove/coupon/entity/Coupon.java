package com.groove.coupon.entity;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰. 할인 계산 규칙을 엔티티에 두어 서비스마다 흩어지지 않게 한다.
 * 발급 수량 경합은 {@code CouponRepository#findByCodeForUpdate} 의 행 락으로 직렬화하고,
 * {@code version} 은 락 없이 읽고 고치는 관리자 수정 경합을 막는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "coupon",
		uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"))
public class Coupon extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "discount_type", nullable = false, length = 20)
	private DiscountType discountType;

	@Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
	private BigDecimal discountValue;

	@Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
	@ColumnDefault("0")
	private BigDecimal minOrderAmount;

	@Column(name = "max_discount_amount", precision = 10, scale = 2)
	private BigDecimal maxDiscountAmount;

	@Column(name = "total_quantity")
	private Integer totalQuantity;

	@Column(name = "issued_count", nullable = false)
	@ColumnDefault("0")
	private int issuedCount;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	@ColumnDefault("'ACTIVE'")
	private CouponStatus status;

	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	private Long version;

	@Builder(access = PRIVATE)
	private Coupon(String code, String name, DiscountType discountType, BigDecimal discountValue,
			BigDecimal minOrderAmount, BigDecimal maxDiscountAmount, Integer totalQuantity,
			LocalDateTime expiresAt) {
		this.code = code;
		this.name = name;
		this.discountType = discountType;
		this.discountValue = discountValue;
		this.minOrderAmount = minOrderAmount;
		this.maxDiscountAmount = maxDiscountAmount;
		this.totalQuantity = totalQuantity;
		this.expiresAt = expiresAt;
		this.status = CouponStatus.ACTIVE;
		this.issuedCount = 0;
	}

	public static Coupon create(String code, String name, DiscountType discountType, BigDecimal discountValue,
			BigDecimal minOrderAmount, BigDecimal maxDiscountAmount, Integer totalQuantity,
			LocalDateTime expiresAt) {
		validateDiscountValue(discountType, discountValue);
		BigDecimal resolvedMinOrderAmount = resolveMinOrderAmount(minOrderAmount);
		BigDecimal resolvedMaxDiscountAmount = resolveMaxDiscountAmount(discountType, maxDiscountAmount);
		validateTotalQuantity(totalQuantity);
		validateExpiresAt(expiresAt);

		return Coupon.builder()
				.code(code)
				.name(name)
				.discountType(discountType)
				.discountValue(discountValue)
				.minOrderAmount(resolvedMinOrderAmount)
				.maxDiscountAmount(resolvedMaxDiscountAmount)
				.totalQuantity(totalQuantity)
				.expiresAt(expiresAt)
				.build();
	}

	public BigDecimal calculateDiscount(BigDecimal orderAmount) {
		if (this.status == CouponStatus.DISABLED) {
			throw new BusinessException(ErrorCode.COUPON_DISABLED);
		}
		if (isExpired()) {
			throw new BusinessException(ErrorCode.COUPON_EXPIRED);
		}
		if (orderAmount.compareTo(this.minOrderAmount) < 0) {
			throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_AMOUNT_NOT_MET);
		}

		BigDecimal discount = this.discountType == DiscountType.FIXED
				? this.discountValue
				: calculateRateDiscount(orderAmount);

		return discount.min(orderAmount).setScale(2, RoundingMode.DOWN);
	}

	public void issueOne() {
		if (this.status == CouponStatus.DISABLED) {
			throw new BusinessException(ErrorCode.COUPON_DISABLED);
		}
		if (isExpired()) {
			throw new BusinessException(ErrorCode.COUPON_EXPIRED);
		}
		if (isSoldOut()) {
			throw new BusinessException(ErrorCode.COUPON_SOLD_OUT);
		}
		this.issuedCount++;
	}

	public void disable() {
		this.status = CouponStatus.DISABLED;
	}

	public void disableAndExpire() {
		this.status = CouponStatus.DISABLED;
		if (!isExpired()) {
			this.expiresAt = LocalDateTime.now();
		}
	}

	public void activate() {
		if (isExpired()) {
			throw new BusinessException(ErrorCode.COUPON_EXPIRED);
		}
		this.status = CouponStatus.ACTIVE;
	}

	public void updateInfo(String name, LocalDateTime expiresAt, Integer totalQuantity) {
		if (!expiresAt.equals(this.expiresAt)) {
			validateExpiresAt(expiresAt);
		}
		if (totalQuantity != null && totalQuantity < this.issuedCount) {
			throw new BusinessException(ErrorCode.COUPON_QUANTITY_BELOW_ISSUED);
		}
		validateTotalQuantity(totalQuantity);

		this.name = name;
		this.expiresAt = expiresAt;
		this.totalQuantity = totalQuantity;
	}

	public void updateDiscount(DiscountType discountType, BigDecimal discountValue, BigDecimal minOrderAmount,
			BigDecimal maxDiscountAmount) {
		if (this.issuedCount > 0) {
			throw new BusinessException(ErrorCode.COUPON_DISCOUNT_LOCKED);
		}
		validateDiscountValue(discountType, discountValue);
		BigDecimal resolvedMinOrderAmount = resolveMinOrderAmount(minOrderAmount);
		BigDecimal resolvedMaxDiscountAmount = resolveMaxDiscountAmount(discountType, maxDiscountAmount);

		this.discountType = discountType;
		this.discountValue = discountValue;
		this.minOrderAmount = resolvedMinOrderAmount;
		this.maxDiscountAmount = resolvedMaxDiscountAmount;
	}

	public boolean isExpired() {
		return this.expiresAt.isBefore(LocalDateTime.now());
	}

	public boolean isSoldOut() {
		return this.totalQuantity != null && this.issuedCount >= this.totalQuantity;
	}

	private BigDecimal calculateRateDiscount(BigDecimal orderAmount) {
		BigDecimal rateDiscount = orderAmount.multiply(this.discountValue)
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
		if (this.maxDiscountAmount == null) {
			return rateDiscount;
		}
		return rateDiscount.min(this.maxDiscountAmount);
	}

	private static void validateDiscountValue(DiscountType discountType, BigDecimal discountValue) {
		if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (discountType == DiscountType.RATE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	private static BigDecimal resolveMinOrderAmount(BigDecimal minOrderAmount) {
		if (minOrderAmount == null) {
			return BigDecimal.ZERO;
		}
		if (minOrderAmount.compareTo(BigDecimal.ZERO) < 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		return minOrderAmount;
	}

	private static BigDecimal resolveMaxDiscountAmount(DiscountType discountType, BigDecimal maxDiscountAmount) {
		if (maxDiscountAmount == null) {
			return null;
		}
		if (maxDiscountAmount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		return discountType == DiscountType.FIXED ? null : maxDiscountAmount;
	}

	private static void validateTotalQuantity(Integer totalQuantity) {
		if (totalQuantity != null && totalQuantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	private static void validateExpiresAt(LocalDateTime expiresAt) {
		if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
