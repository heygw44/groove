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
 * 반품(claim) — 배송완료 후 변심 환불의 역물류 aggregate root (#239).
 *
 * <p>주문/품목을 참조하되 {@code OrderStatus} 와 분리된 별도 상태머신({@link ClaimStatus})을 가진다 — 반품 상태를
 * 주문 상태에 섞으면 상태 폭발이 일어나기 때문이다. 발송 전 즉시 취소({@code AdminOrderService.refund})와 달리
 * 회수·검수 비용을 동반하는 별도 유스케이스이므로 경로를 분리한다.
 *
 * <p>상태 전이는 {@code approve}/{@code startTransit}/{@code startInspecting}/{@code markRefunded}/{@code reject}
 * 단일 진입점만 허용하고, 합법 전이는 {@link ClaimStatus#canTransitionTo} 가 판정한다 — 위반 시
 * {@link ClaimInvalidStateTransitionException}(409). 한 주문에 여러 반품이 있을 수 있어 order_id 는 UNIQUE 가
 * 아니며(거부 후 재요청 허용), 활성 반품 중복은 {@code ClaimService} 가 잔여 수량 회계로 가드한다.
 *
 * <p>{@link ClaimItem} 은 aggregate child — cascade=ALL + orphanRemoval=true 로 Claim 을 통해서만 변경된다.
 */
@Entity
@Table(name = "claim")
public class Claim extends BaseTimeEntity {

    /** DB {@code reason}/{@code rejection_reason} 컬럼 길이 — 정적 팩토리/거부 메서드가 선검증한다. */
    static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status;

    @Column(name = "reason", nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "rejection_reason", length = MAX_REASON_LENGTH)
    private String rejectionReason;

    /** 확정 환불액 (#239) — REFUNDED 전이 시 {@code ClaimService} 가 비례 배분으로 계산해 기록한다. 그 전엔 0. */
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

    private Claim(Order order, String reason) {
        this.order = order;
        this.reason = reason;
        this.status = ClaimStatus.REQUESTED;
        this.refundAmount = 0L;
    }

    /**
     * 반품 접수 — 초기 상태 REQUESTED. 접수 자격(주문 상태/소유/기한)·항목 잔여 수량 검증은 호출 측
     * ({@code ClaimService}) 이 끝낸 상태로 호출한다. 항목은 {@link #addItem} 으로 붙인다. 접수 시각은
     * {@code BaseTimeEntity.createdAt} 으로 대체한다(별도 컬럼 불필요).
     */
    public static Claim request(Order order, String reason) {
        Objects.requireNonNull(order, "order must not be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason length must be <= " + MAX_REASON_LENGTH);
        }
        return new Claim(order, reason);
    }

    /** 반품 항목 추가 (접수 시점에만 호출). */
    public void addItem(ClaimItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        item.attachTo(this);
        items.add(item);
    }

    /** 반품 승인 (관리자) — REQUESTED → APPROVED. */
    public void approve(Instant now) {
        transitionTo(ClaimStatus.APPROVED);
        this.approvedAt = now;
    }

    /** 회수 시작 (스케줄러 자동) — APPROVED → IN_TRANSIT. */
    public void startTransit(Instant now) {
        transitionTo(ClaimStatus.IN_TRANSIT);
        this.inTransitAt = now;
    }

    /** 검수 시작 (스케줄러 자동) — IN_TRANSIT → INSPECTING. */
    public void startInspecting(Instant now) {
        transitionTo(ClaimStatus.INSPECTING);
        this.inspectingAt = now;
    }

    /** 환불 확정 (검수 통과) — INSPECTING → REFUNDED. 확정 환불액과 종착 시각을 기록한다. */
    public void markRefunded(long refundAmount, Instant now) {
        if (refundAmount < 0) {
            throw new IllegalArgumentException("refundAmount must be non-negative: " + refundAmount);
        }
        transitionTo(ClaimStatus.REFUNDED);
        this.refundAmount = refundAmount;
        this.completedAt = now;
    }

    /** 반품 거부 (관리자) — REQUESTED 또는 INSPECTING(검수 불합격) → REJECTED. 사유와 종착 시각을 기록한다. */
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

    /** 이 반품의 정가 합 — 항목별 {@code getGross()} 합. claim 환불액 비례 배분의 분자. */
    public long getGross() {
        return items.stream().mapToLong(ClaimItem::getGross).sum();
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
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
