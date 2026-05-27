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
 * 회원 보유 쿠폰 — 정책({@link Coupon}) 의 발급 인스턴스 (ERD §4.16, docs/plans/coupon-system.md §3).
 *
 * <p>{@code UNIQUE(coupon_id, member_id)} 로 회원당 동일 쿠폰 1장을 DB 가 강제한다 (설계 §7).
 * 사용 주문은 {@code orderId} 단방향 역참조로 추적한다 — {@code orders} 에 쿠폰 FK 를 두지 않아
 * 순환 FK 와 삽입 순서 의존성을 피한다 (설계 §7).
 *
 * <p>{@code memberId}·{@code orderId} 는 {@link com.groove.order.domain.Order} 와 동일하게 연관
 * 매핑 없이 식별자 컬럼으로만 둔다 (FK 는 DB 레벨). {@code coupon} 만 할인 규칙 접근을 위해
 * {@link ManyToOne} 으로 매핑한다.
 *
 * <p>상태 전이는 {@link #use}/{@link #restore}/{@link #expire}/{@link #cancel} 가드 메서드만
 * 허용한다 — {@link MemberCouponStatus#canTransitionTo} 위반 시
 * {@link IllegalCouponStateTransitionException}. 발급 오케스트레이션(선착순 동시성)은 #90.
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
     * 발급. 초기 상태 ISSUED, {@code expiresAt} 은 발급 시점 {@code coupon.validUntil} 스냅샷이다
     * (설계: 발급 시 valid_until 스냅샷). 발급 가능 여부(소진·중복·정책상태) 검증은 서비스(#90)가 한다.
     */
    public static MemberCoupon issue(Coupon coupon, Long memberId) {
        Objects.requireNonNull(coupon, "coupon must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        return new MemberCoupon(coupon, memberId, Instant.now(), coupon.getValidUntil());
    }

    /**
     * 사용 (ISSUED → USED). 사용 주문 식별자와 사용 시각을 기록한다.
     */
    public void use(Long orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        transitionTo(MemberCouponStatus.USED);
        this.usedAt = Instant.now();
        this.orderId = orderId;
    }

    /**
     * 복원 (USED → ISSUED). 주문 취소/환불 시 사용 흔적(usedAt·orderId)을 비운다.
     */
    public void restore() {
        transitionTo(MemberCouponStatus.ISSUED);
        this.usedAt = null;
        this.orderId = null;
    }

    /** 만료 (ISSUED → EXPIRED). 스케줄러 배치(#92~)가 호출한다. */
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
