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
 * 쿠폰 정책 (ERD §4.15, docs/plans/coupon-system.md §3).
 *
 * <p>할인 규칙(정액/정률·상한·최소주문금액)과 발급 제약(한정수량·회원당 한도·유효기간)을 담는 정책
 * 엔티티다. 정책 1건이 N명의 {@link MemberCoupon} 으로 발급된다. 핫 카운터 {@code issuedCount} 의
 * 동시성 제어(선착순 발급)는 후속 이슈(#90)에서 다룬다 — 본 이슈는 모델·할인계산·상태머신만 다룬다.
 *
 * <p>도메인 가드는 DB CHECK 제약(V14)의 이중 방어선이다 — {@link OrderItem} 과 동일하게 생성 시점에
 * 한 번 검증한다. 상태 변경은 {@link #changeStatus(CouponStatus)} 단일 진입점만 허용한다.
 */
@Entity
@Table(name = "coupon")
public class Coupon extends BaseTimeEntity {

    /** 정률(PERCENTAGE) 할인율 허용 하한. */
    private static final long PERCENTAGE_MIN = 1L;
    /** 정률(PERCENTAGE) 할인율 허용 상한. */
    private static final long PERCENTAGE_MAX = 100L;

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

    /**
     * 쿠폰 정책 빌더 진입점. 필수값(이름·할인방식·할인값·유효기간)을 받고, 선택값(상한·최소주문금액·
     * 한정수량·회원당한도)은 {@link Builder} 의 fluent 메서드로 설정한다. 검증은 {@link Builder#build()}.
     */
    public static Builder builder(String name, CouponDiscountType discountType, long discountValue,
                                  Instant validFrom, Instant validUntil) {
        return new Builder(name, discountType, discountValue, validFrom, validUntil);
    }

    /**
     * 주문 소계(subtotal) 에 대한 할인액을 계산한다.
     *
     * <ul>
     *   <li>{@code subtotal < minOrderAmount} → {@link CouponMinOrderNotMetException} (422).</li>
     *   <li>{@link CouponDiscountType} 에 원시 할인액 산정을 위임한다.</li>
     *   <li>결과는 {@code min(raw, subtotal)} — {@code discount ≤ subtotal} 불변식으로
     *       {@code payable = subtotal − discount ≥ 0} 을 보장한다.</li>
     * </ul>
     */
    public long calculateDiscount(long subtotal) {
        if (subtotal < minOrderAmount) {
            throw new CouponMinOrderNotMetException(subtotal, minOrderAmount);
        }
        long raw = discountType.rawDiscount(subtotal, discountValue, maxDiscountAmount);
        return Math.min(raw, subtotal);
    }

    /**
     * 지정 시각 기준 발급 가능 여부 — 정책 상태가 {@link CouponStatus#ACTIVE} 이고 현재가 발급 기간
     * ({@code validFrom ≤ now ≤ validUntil}) 안인지를 판단한다. 소진({@code issuedCount}) 여부는
     * 포함하지 않는다 — 소진은 발급 경로의 원자적 조건부 UPDATE 가 판정한다 (#90, 설계 §4).
     */
    public boolean isIssuable(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status == CouponStatus.ACTIVE
                && !now.isBefore(validFrom)
                && !now.isAfter(validUntil);
    }

    /**
     * 남은 발급 수량 — {@code totalQuantity − issuedCount}. 무제한({@code totalQuantity == null})이면
     * {@code null}. {@code CouponResponse.remainingQuantity} 산정에 쓰인다 (API.md §3.9).
     */
    public Integer remainingQuantity() {
        return totalQuantity == null ? null : totalQuantity - issuedCount;
    }

    /**
     * 발급 슬롯 1개를 차감 시도 — 한정수량 여유가 있으면(또는 무제한) {@code issuedCount} 를 증가시키고
     * {@code true}, 소진이면 {@code false} 를 반환한다 (read-check-increment).
     *
     * <p><b>동시성 주의</b>: 본 메서드는 그 자체로 원자적이지 않다 — 정확성은 호출자의 잠금 전략에
     * 달려 있다 (설계 §4). 비관적 락 경로({@code findByIdForUpdate})는 행 락으로 안전하고, 락 없는
     * 베이스라인 경로는 lost-update 로 <b>초과발급을 재현</b>한다. 프로덕션 발급 경로는 본 메서드 대신
     * {@link CouponRepository#incrementIssuedCount(Long)} 의 원자적 조건부 UPDATE 를 쓴다.
     */
    public boolean tryIssueOne() {
        if (totalQuantity != null && issuedCount >= totalQuantity) {
            return false;
        }
        this.issuedCount++;
        return true;
    }

    /**
     * 상태 전이 단일 진입점. {@link CouponStatus#canTransitionTo} 가 false 면
     * {@link IllegalCouponStateTransitionException}.
     */
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

    /**
     * 쿠폰 정책 빌더. 초기 상태 ACTIVE, {@code issuedCount=0}.
     *
     * <p>선택값 기본: 상한 없음(무제한), 최소주문금액 0, 한정수량 {@code null}(무제한 발급),
     * 회원당 1장. 회원당 한도는 v1 에서 1 로 고정되며 {@code UNIQUE(coupon_id, member_id)} 로 DB 가
     * 강제한다 (설계 §7). {@link #build()} 가 DB CHECK 제약(V14)의 이중 방어로 값을 검증한다.
     */
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

        /** 정률 할인 상한(원). {@code null} 이면 무제한. */
        public Builder maxDiscountAmount(Long maxDiscountAmount) {
            this.maxDiscountAmount = maxDiscountAmount;
            return this;
        }

        /** 최소 주문금액(원). 기본 0. */
        public Builder minOrderAmount(long minOrderAmount) {
            this.minOrderAmount = minOrderAmount;
            return this;
        }

        /** 한정 발급 수량. {@code null} 이면 무제한 발급. */
        public Builder totalQuantity(Integer totalQuantity) {
            this.totalQuantity = totalQuantity;
            return this;
        }

        /** 회원당 발급 한도. 기본 1 (v1 고정). */
        public Builder perMemberLimit(int perMemberLimit) {
            this.perMemberLimit = perMemberLimit;
            return this;
        }

        /**
         * @throws IllegalArgumentException 정책 값이 DB CHECK 제약(V14)을 위반하는 경우
         */
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
            if (maxDiscountAmount != null && maxDiscountAmount <= 0) {
                throw new IllegalArgumentException(
                        "maxDiscountAmount must be positive when present: " + maxDiscountAmount);
            }
            if (minOrderAmount < 0) {
                throw new IllegalArgumentException("minOrderAmount must be non-negative: " + minOrderAmount);
            }
            if (totalQuantity != null && totalQuantity < 0) {
                throw new IllegalArgumentException("totalQuantity must be non-negative: " + totalQuantity);
            }
            if (perMemberLimit <= 0) {
                throw new IllegalArgumentException("perMemberLimit must be positive: " + perMemberLimit);
            }
            Objects.requireNonNull(validFrom, "validFrom must not be null");
            Objects.requireNonNull(validUntil, "validUntil must not be null");
            if (!validUntil.isAfter(validFrom)) {
                throw new IllegalArgumentException("validUntil must be after validFrom");
            }
        }
    }
}
