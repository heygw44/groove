package com.groove.payment.domain;

import com.groove.common.persistence.BaseTimeEntity;
import com.groove.order.domain.Order;
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
 * 결제 — 주문당 1건(order_id UNIQUE). 생성은 initiate(PENDING), 확정은 markPaid(PAID)/markFailed(FAILED).
 * 상태 전이 판정은 PaymentStatus.canTransitionTo.
 */
@Entity
@Table(name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_pg_tx", columnNames = "pg_transaction_id"))
public class Payment extends BaseTimeEntity {

    /** pg_provider 컬럼 길이. */
    static final int MAX_PG_PROVIDER_LENGTH = 20;
    /** pg_transaction_id 컬럼 길이. */
    static final int MAX_PG_TRANSACTION_ID_LENGTH = 100;
    /** failure_reason 컬럼 길이. */
    static final int MAX_FAILURE_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "pg_provider", nullable = false, length = MAX_PG_PROVIDER_LENGTH)
    private String pgProvider;

    @Column(name = "pg_transaction_id", length = MAX_PG_TRANSACTION_ID_LENGTH)
    private String pgTransactionId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * 누적 환불액. markRefunded()는 amount 전액을, refund(long, Instant)는 claim 환불액을 누적한다.
     * refundedAmount == amount 면 REFUNDED, 그 전엔 PARTIALLY_REFUNDED.
     */
    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    protected Payment() {
    }

    private Payment(Order order, long amount, PaymentMethod method, String pgProvider, String pgTransactionId) {
        this.order = order;
        this.amount = amount;
        this.method = method;
        this.pgProvider = pgProvider;
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * 결제 접수 — PENDING 으로 생성한다. amount 양수, pgProvider/pgTransactionId blank 불가·길이 제한 검증.
     */
    public static Payment initiate(Order order, long amount, PaymentMethod method,
                                   String pgProvider, String pgTransactionId) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(method, "method must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        if (pgProvider == null || pgProvider.isBlank()) {
            throw new IllegalArgumentException("pgProvider must not be blank");
        }
        if (pgProvider.length() > MAX_PG_PROVIDER_LENGTH) {
            throw new IllegalArgumentException("pgProvider length must be <= " + MAX_PG_PROVIDER_LENGTH);
        }
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId must not be blank");
        }
        if (pgTransactionId.length() > MAX_PG_TRANSACTION_ID_LENGTH) {
            throw new IllegalArgumentException("pgTransactionId length must be <= " + MAX_PG_TRANSACTION_ID_LENGTH);
        }
        return new Payment(order, amount, method, pgProvider, pgTransactionId);
    }

    /**
     * 결제 완료 확정 — PAID 로 전이하고 paidAt 을 주입된 now 로 기록한다. 전이 위반 시 IllegalStateException.
     */
    public void markPaid(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        transitionTo(PaymentStatus.PAID);
        this.paidAt = now;
    }

    /**
     * 결제 실패 확정 — FAILED 로 전이하고 failureReason 을 길이 제한 내로 잘라 기록한다(null 허용).
     */
    public void markFailed(String failureReason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = truncate(failureReason);
    }

    /**
     * 전액 환불 확정 — REFUNDED 로 전이하고 refundedAmount 를 amount 전액으로 설정한다.
     * 전이 위반 시 IllegalStateException.
     */
    public void markRefunded() {
        transitionTo(PaymentStatus.REFUNDED);
        this.refundedAmount = this.amount;
    }

    /**
     * 부분/전액 환불 확정 — claim 환불액(amount, 양수)을 누적한다. 누적액이 전액에 도달하면 REFUNDED,
     * 아직 일부면 PARTIALLY_REFUNDED 로 전이한다. 전이 위반·환불액 초과 시 예외. now 는 미저장.
     */
    public void refund(long amount, Instant now) {
        if (amount <= 0) {
            throw new IllegalArgumentException("환불액은 양수여야 합니다: " + amount);
        }
        // 뺄셈으로 비교해 덧셈 오버플로를 피한다.
        if (amount > this.amount - this.refundedAmount) {
            throw new IllegalArgumentException(
                    "누적 환불액이 결제액을 초과합니다: 기환불=" + this.refundedAmount + ", 요청=" + amount + ", 결제액=" + this.amount);
        }
        long next = this.refundedAmount + amount;
        PaymentStatus target = next == this.amount ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        // 상태가 그대로면 전이를 건너뛰고 누적액만 갱신한다.
        if (this.status != target) {
            transitionTo(target);
        }
        this.refundedAmount = next;
    }

    /**
     * PG 환불 호출용 멱등 키 "refund:{paymentId}:{pgTransactionId}" 를 조립한다.
     * 영속화 전(id == null)이면 IllegalStateException.
     */
    public String refundIdempotencyKey() {
        if (id == null) {
            throw new IllegalStateException("영속화 전 결제는 환불 멱등 키를 생성할 수 없습니다 (id=null)");
        }
        return "refund:" + id + ":" + pgTransactionId;
    }

    /**
     * 부분 반품 PG 환불 호출용 멱등 키 "refund:{paymentId}:claim:{claimId}" 를 조립한다.
     * 영속화 전(id == null)이거나 claimId 가 null 이면 예외.
     */
    public String refundIdempotencyKey(Long claimId) {
        if (id == null) {
            throw new IllegalStateException("영속화 전 결제는 환불 멱등 키를 생성할 수 없습니다 (id=null)");
        }
        if (claimId == null) {
            throw new IllegalArgumentException("claimId must not be null");
        }
        return "refund:" + id + ":claim:" + claimId;
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 결제 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
    }

    private static String truncate(String failureReason) {
        if (failureReason == null) {
            return null;
        }
        return failureReason.length() <= MAX_FAILURE_REASON_LENGTH
                ? failureReason
                : failureReason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public long getAmount() {
        return amount;
    }

    public long getRefundedAmount() {
        return refundedAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getPgProvider() {
        return pgProvider;
    }

    public String getPgTransactionId() {
        return pgTransactionId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
