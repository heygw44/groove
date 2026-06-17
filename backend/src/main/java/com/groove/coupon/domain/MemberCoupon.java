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

import java.time.Instant;
import java.util.Objects;

/**
 * 회원 보유 쿠폰 — 정책(Coupon) 의 발급 인스턴스.
 *
 * <p>UNIQUE(coupon_id, member_id) 로 회원당 동일 쿠폰 1장을 DB 가 강제한다. 사용 주문은 orderId 단방향
 * 역참조로 추적한다.
 *
 * <p>memberId·orderId 는 연관 매핑 없이 식별자 컬럼으로만 둔다. coupon 만 ManyToOne 으로 매핑한다.
 *
 * <p>상태 전이는 use/restore/expire/cancel 가드 메서드만 허용한다 — MemberCouponStatus.canTransitionTo
 * 위반 시 IllegalCouponStateTransitionException.
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

    /**
     * 발급. 초기 상태 ISSUED, issuedAt 은 주입된 now, expiresAt 은 발급 시점 coupon.validUntil 스냅샷이다.
     */
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
     * 복원 (USED → ISSUED, 이미 만료됐으면 USED → EXPIRED). 주문 취소/환불 시 사용 흔적(usedAt·orderId)을
     * 비운다. 만료 판정은 strict 비교(expiresAt < now).
     */
    public void restore(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        transitionTo(expiresAt.isBefore(now) ? MemberCouponStatus.EXPIRED : MemberCouponStatus.ISSUED);
        this.usedAt = null;
        this.orderId = null;
    }

    /**
     * 만료 (ISSUED → EXPIRED).
     *
     * <p>운영 만료 배치는 MemberCouponRepository.expireOverdueBatch 벌크 UPDATE 로 본 메서드를 우회한다.
     * 본 메서드는 단건 만료 경로용 진입점이다.
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
