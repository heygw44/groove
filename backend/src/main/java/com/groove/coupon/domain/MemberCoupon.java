package com.groove.coupon.domain;

import com.groove.common.persistence.BaseTimeEntity;
import com.groove.coupon.exception.IllegalCouponStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 회원 보유 쿠폰 — 정책(Coupon)의 발급 인스턴스. UNIQUE(coupon_id, member_id)로 회원당 1장을 DB 가 강제한다.
 * memberId·orderId 는 식별자 컬럼으로만 두고 coupon 만 ManyToOne 매핑(슬라이스 단방향).
 */
@Entity
@Table(name = "member_coupon", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_coupon_coupon_member",
                columnNames = {"coupon_id", "member_id"})
})
public class MemberCoupon extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MemberCouponStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "order_id")
    private Long orderId;

    protected MemberCoupon() {
    }

    private MemberCoupon(Coupon coupon, Long memberId, Instant issuedAt, Instant expiresAt) {
        this.coupon = coupon;
        this.memberId = memberId;
        this.status = MemberCouponStatus.ISSUED;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /** expiresAt 은 발급 시점 coupon.validUntil 스냅샷. */
    public static MemberCoupon issue(Coupon coupon, Long memberId, Instant now) {
        Objects.requireNonNull(coupon, "coupon must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new MemberCoupon(coupon, memberId, now, coupon.getValidUntil());
    }

    public void use(Long orderId, Instant now) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        transitionTo(MemberCouponStatus.USED);
        this.usedAt = now;
        this.orderId = orderId;
    }

    /**
     * 복원 (USED → ISSUED). 이미 만료됐어도 소멸 않고 expiresAt 을 now + grace 로 연장해 되살린다
     * (만료 직전 사용 후 취소로 쿠폰을 잃지 않게). USED 아니면 no-op 으로 무락 동시 복원에 안전.
     */
    public void restore(Instant now, Duration grace) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(grace, "grace must not be null");
        if (status != MemberCouponStatus.USED) {
            return;
        }
        if (expiresAt.isBefore(now)) {
            this.expiresAt = now.plus(grace);
        }
        transitionTo(MemberCouponStatus.ISSUED);
        this.usedAt = null;
        this.orderId = null;
    }

    /** 단건 만료 경로 — 운영 배치는 expireOverdueBatch 벌크 UPDATE 로 우회. */
    public void expire() {
        transitionTo(MemberCouponStatus.EXPIRED);
    }

    public void cancel() {
        transitionTo(MemberCouponStatus.CANCELLED);
    }

    private void transitionTo(MemberCouponStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalCouponStateTransitionException(status, next);
        }
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public Coupon getCoupon() {
        return coupon;
    }

    public Long getMemberId() {
        return memberId;
    }

    public MemberCouponStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Long getOrderId() {
        return orderId;
    }
}
