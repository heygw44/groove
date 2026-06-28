package com.groove.claim.domain;

import com.groove.claim.exception.ClaimInvalidStateTransitionException;
import com.groove.common.persistence.BaseTimeEntity;
import com.groove.order.domain.Order;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 반품(claim) aggregate root — 주문/품목을 참조하되 ClaimStatus 별도 상태머신을 가진다.
 * 상태 전이는 transitionTo 를 거치며 불법 전이면 ClaimInvalidStateTransitionException(409).
 */
@Entity
@Table(name = "claim")
public class Claim extends BaseTimeEntity {

    static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 취소(발송 전)/반품(발송 후). */
    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 20)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "reason", nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "rejection_reason", length = MAX_REASON_LENGTH)
    private String rejectionReason;

    /** REFUNDED 전이 시 기록, 그 전엔 0. */
    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "in_transit_at")
    private Instant inTransitAt;

    @Column(name = "inspecting_at")
    private Instant inspectingAt;

    /** 종착(REFUNDED/REJECTED) 시각. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ClaimItem> items = new ArrayList<>();

    protected Claim() {
    }

    private Claim(Order order, ClaimType claimType, String reason) {
        this.order = order;
        this.claimType = claimType;
        this.reason = reason;
        this.status = ClaimStatus.REQUESTED;
        this.refundAmount = 0L;
    }

    /** 항목은 addItem 으로 붙인다. */
    public static Claim request(Order order, String reason) {
        return create(order, ClaimType.RETURN, reason);
    }

    /** 발송 전 부분 취소. */
    public static Claim requestCancellation(Order order, String reason) {
        return create(order, ClaimType.CANCEL, reason);
    }

    private static Claim create(Order order, ClaimType claimType, String reason) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(claimType, "claimType must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason length must be <= " + MAX_REASON_LENGTH);
        }
        return new Claim(order, claimType, reason);
    }

    /** 접수 시점에만 호출. */
    public void addItem(ClaimItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        item.attachTo(this);
        items.add(item);
    }

    public void approve(Instant now) {
        transitionTo(ClaimStatus.APPROVED);
        this.approvedAt = now;
    }

    public void startTransit(Instant now) {
        transitionTo(ClaimStatus.IN_TRANSIT);
        this.inTransitAt = now;
    }

    public void startInspecting(Instant now) {
        transitionTo(ClaimStatus.INSPECTING);
        this.inspectingAt = now;
    }

    public void markRefunded(long refundAmount, Instant now) {
        if (refundAmount < 0) {
            throw new IllegalArgumentException("refundAmount must be non-negative: " + refundAmount);
        }
        transitionTo(ClaimStatus.REFUNDED);
        this.refundAmount = refundAmount;
        this.completedAt = now;
    }

    /** CANCEL 클레임 REQUESTED → REFUNDED 직행. 일반 상태머신 대신 타입·상태를 직접 가드. */
    public void markCancelRefunded(long refundAmount, Instant now) {
        if (claimType != ClaimType.CANCEL || status != ClaimStatus.REQUESTED) {
            throw new ClaimInvalidStateTransitionException(status, ClaimStatus.REFUNDED);
        }
        if (refundAmount < 0) {
            throw new IllegalArgumentException("refundAmount must be non-negative: " + refundAmount);
        }
        this.status = ClaimStatus.REFUNDED;
        this.refundAmount = refundAmount;
        this.completedAt = now;
    }

    /** REQUESTED 또는 INSPECTING(검수 불합격) → REJECTED. */
    public void reject(String reason, Instant now) {
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            reason = reason.substring(0, MAX_REASON_LENGTH);
        }
        transitionTo(ClaimStatus.REJECTED);
        this.rejectionReason = reason;
        this.completedAt = now;
    }

    private void transitionTo(ClaimStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ClaimInvalidStateTransitionException(status, next);
        }
        this.status = next;
    }

    /** 항목별 정가 합. */
    public long getGross() {
        return items.stream().mapToLong(ClaimItem::getGross).sum();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public long getRefundAmount() {
        return refundAmount;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getInTransitAt() {
        return inTransitAt;
    }

    public Instant getInspectingAt() {
        return inspectingAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<ClaimItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
