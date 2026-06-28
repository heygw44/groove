package com.groove.coupon.domain;

import com.groove.common.persistence.BaseTimeEntity;
import com.groove.coupon.exception.CouponMinOrderNotMetException;
import com.groove.coupon.exception.IllegalCouponStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * 쿠폰 정책 — 할인 규칙과 발급 제약. 정책 1건이 N명의 MemberCoupon 으로 발급된다.
 * 도메인 가드는 생성 시점에 검증, 상태 변경은 changeStatus 단일 진입점.
 */
@Entity
@Table(name = "coupon")
public class Coupon extends BaseTimeEntity {

    private static final long PERCENTAGE_MIN = 1L;
    private static final long PERCENTAGE_MAX = 100L;
    /** 정액 할인 상한 — 운영 실수로 사실상 전액 무료 쿠폰이 생성되는 것을 막는 위생 가드. */
    private static final long FIXED_AMOUNT_DISCOUNT_MAX = 1_000_000L;
    /** 회원당 1장은 uk_member_coupon_coupon_member UNIQUE 가 강제. */
    private static final int PER_MEMBER_LIMIT_ONLY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "per_member_limit", nullable = false)
    private int perMemberLimit;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CouponStatus status;

    protected Coupon() {
    }

    private Coupon(Builder builder) {
        this.name = builder.name;
        this.discountType = builder.discountType;
        this.discountValue = builder.discountValue;
        this.maxDiscountAmount = builder.maxDiscountAmount;
        this.minOrderAmount = builder.minOrderAmount;
        this.totalQuantity = builder.totalQuantity;
        this.issuedCount = 0;
        this.perMemberLimit = builder.perMemberLimit;
        this.validFrom = builder.validFrom;
        this.validUntil = builder.validUntil;
        this.status = CouponStatus.ACTIVE;
    }

    /** 필수값을 받고 선택값은 fluent 로, 검증은 build(). */
    public static Builder builder(String name, CouponDiscountType discountType, long discountValue,
                                  Instant validFrom, Instant validUntil) {
        return new Builder(name, discountType, discountValue, validFrom, validUntil);
    }

    /** 주문 소계 할인액 = min(타입별 원시 할인, subtotal). 최소주문금액 미달이면 예외. */
    public long calculateDiscount(long subtotal) {
        if (subtotal < minOrderAmount) {
            throw new CouponMinOrderNotMetException(subtotal, minOrderAmount);
        }
        long raw = discountType.rawDiscount(subtotal, discountValue, maxDiscountAmount);
        return Math.min(raw, subtotal);
    }

    /** ACTIVE 이고 발급 기간 내인지. 소진 여부는 보지 않는다. */
    public boolean isIssuable(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status == CouponStatus.ACTIVE
                && !now.isBefore(validFrom)
                && !now.isAfter(validUntil);
    }

    /** 남은 발급 수량. 무제한이면 null. */
    public Integer remainingQuantity() {
        return totalQuantity == null ? null : Math.max(0, totalQuantity - issuedCount);
    }

    /** read-check-increment 발급 슬롯 차감. 그 자체로는 원자적이지 않다. */
    public boolean tryIssueOne() {
        if (totalQuantity != null && issuedCount >= totalQuantity) {
            return false;
        }
        this.issuedCount++;
        return true;
    }

    /** 상태 전이 단일 진입점. 불법 전이면 예외. */
    public void changeStatus(CouponStatus next) {
        Objects.requireNonNull(next, "next status must not be null");
        if (!status.canTransitionTo(next)) {
            throw new IllegalCouponStateTransitionException(status, next);
        }
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CouponDiscountType getDiscountType() {
        return discountType;
    }

    public long getDiscountValue() {
        return discountValue;
    }

    public Long getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public long getMinOrderAmount() {
        return minOrderAmount;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public int getIssuedCount() {
        return issuedCount;
    }

    public int getPerMemberLimit() {
        return perMemberLimit;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public CouponStatus getStatus() {
        return status;
    }

    /** 선택값 기본: 상한 없음, 최소주문금액 0, 무제한 발급, 회원당 1장. */
    public static final class Builder {

        private final String name;
        private final CouponDiscountType discountType;
        private final long discountValue;
        private final Instant validFrom;
        private final Instant validUntil;
        private Long maxDiscountAmount;
        private long minOrderAmount = 0L;
        private Integer totalQuantity;
        private int perMemberLimit = 1;

        private Builder(String name, CouponDiscountType discountType, long discountValue,
                        Instant validFrom, Instant validUntil) {
            this.name = name;
            this.discountType = discountType;
            this.discountValue = discountValue;
            this.validFrom = validFrom;
            this.validUntil = validUntil;
        }

        /** null 이면 무제한. */
        public Builder maxDiscountAmount(Long maxDiscountAmount) {
            this.maxDiscountAmount = maxDiscountAmount;
            return this;
        }

        public Builder minOrderAmount(long minOrderAmount) {
            this.minOrderAmount = minOrderAmount;
            return this;
        }

        /** null 이면 무제한 발급. */
        public Builder totalQuantity(Integer totalQuantity) {
            this.totalQuantity = totalQuantity;
            return this;
        }

        public Builder perMemberLimit(int perMemberLimit) {
            this.perMemberLimit = perMemberLimit;
            return this;
        }

        public Coupon build() {
            validate();
            return new Coupon(this);
        }

        private void validate() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            Objects.requireNonNull(discountType, "discountType must not be null");
            if (discountValue <= 0) {
                throw new IllegalArgumentException("discountValue must be positive: " + discountValue);
            }
            if (discountType == CouponDiscountType.PERCENTAGE
                    && (discountValue < PERCENTAGE_MIN || discountValue > PERCENTAGE_MAX)) {
                throw new IllegalArgumentException(
                        "percentage discountValue must be 1..100: " + discountValue);
            }
            if (discountType == CouponDiscountType.FIXED_AMOUNT && discountValue > FIXED_AMOUNT_DISCOUNT_MAX) {
                throw new IllegalArgumentException(
                        "FIXED_AMOUNT discountValue must be <= " + FIXED_AMOUNT_DISCOUNT_MAX + ": " + discountValue);
            }
            if (maxDiscountAmount != null && maxDiscountAmount <= 0) {
                throw new IllegalArgumentException(
                        "maxDiscountAmount must be positive when present: " + maxDiscountAmount);
            }
            // 정률 할인은 상한 필수(무한 할인 방지).
            if (discountType == CouponDiscountType.PERCENTAGE && maxDiscountAmount == null) {
                throw new IllegalArgumentException(
                        "PERCENTAGE coupon requires maxDiscountAmount to bound the discount");
            }
            if (minOrderAmount < 0) {
                throw new IllegalArgumentException("minOrderAmount must be non-negative: " + minOrderAmount);
            }
            if (totalQuantity != null && totalQuantity <= 0) {
                throw new IllegalArgumentException("totalQuantity must be positive when present: " + totalQuantity);
            }
            // 회원당 1장은 UNIQUE 제약이 강제하므로 perMemberLimit 는 1 만 허용(설정-동작 모순 차단). 컬럼은 확장 여지로 보존.
            if (perMemberLimit != PER_MEMBER_LIMIT_ONLY) {
                throw new IllegalArgumentException(
                        "perMemberLimit must be 1 (per-member multi-issue is not supported): " + perMemberLimit);
            }
            Objects.requireNonNull(validFrom, "validFrom must not be null");
            Objects.requireNonNull(validUntil, "validUntil must not be null");
            if (!validUntil.isAfter(validFrom)) {
                throw new IllegalArgumentException("validUntil must be after validFrom");
            }
        }
    }
}
