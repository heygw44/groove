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
 * 회원 보유 쿠폰 — 정책(Coupon) 의 발급 인스턴스. UNIQUE(coupon_id, member_id) 로 회원당 동일 쿠폰 1장을 DB 가 강제한다.
 * memberId·orderId 는 식별자 컬럼으로만 두고 coupon 만 ManyToOne 으로 매핑한다.
 * 상태 전이는 use/restore/expire/cancel 가드 메서드만 허용한다(위반 시 IllegalCouponStateTransitionException).
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

    /** 발급. 초기 상태 ISSUED, issuedAt 은 주입된 now, expiresAt 은 발급 시점 coupon.validUntil 스냅샷이다. */
    public static MemberCoupon issue(Coupon coupon, Long memberId, Instant now) {
        Objects.requireNonNull(coupon, "coupon must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new MemberCoupon(coupon, memberId, now, coupon.getValidUntil());
    }

    /** 사용 (ISSUED → USED). 사용 주문 식별자와 사용 시각(주입된 now)을 기록한다. */
    public void use(Long orderId, Instant now) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        transitionTo(MemberCouponStatus.USED);
        this.usedAt = now;
        this.orderId = orderId;
    }

    /**
     * 복원 (USED → ISSUED). 주문 취소/환불 시 사용 흔적(usedAt·orderId)을 비운다. 복원 시점에 이미 만료됐어도
     * 소멸시키지 않고 expiresAt 을 now + grace 로 연장해 되살린다(만료 직전 사용 후 취소로 쿠폰을 잃지 않게).
     * 멱등: USED 가 아니면 no-op — 무락 동시 복원의 두 번째 호출이 트랜잭션을 롤백시키지 않도록 한다.
     */
    public void restore(Instant now, Duration grace) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(grace, "grace must not be null");
        if (status != MemberCouponStatus.USED) {
            return; // 멱등: 이미 복원/소멸된 쿠폰은 건드리지 않는다.
        }
        if (expiresAt.isBefore(now)) {
            this.expiresAt = now.plus(grace);
        }
        transitionTo(MemberCouponStatus.ISSUED);
        this.usedAt = null;
        this.orderId = null;
    }

    /**
     * 만료 (ISSUED → EXPIRED). 단건 만료 경로용 진입점 — 운영 만료 배치는 expireOverdueBatch 벌크 UPDATE 로 우회한다.
     */
    public void expire() {
        transitionTo(MemberCouponStatus.EXPIRED);
    }

    /** 발급 취소 (ISSUED → CANCELLED). */
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
