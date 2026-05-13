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

import java.time.Instant;
import java.util.Objects;

/**
 * 결제 (ERD §4.11, glossary §2.8).
 *
 * <p>주문당 결제 1건 — {@code order_id} UNIQUE. 결제 재시도는 새 row 가 아니라 기존 row 의 상태 갱신이다
 * (ERD §4.11). 생성은 {@link #initiate}(PENDING), 확정은 {@link #markPaid()}(PAID) / {@link #markFailed(String)}(FAILED).
 *
 * <p>상태 전이 규칙은 {@link PaymentStatus#canTransitionTo(PaymentStatus)} 가 판정한다 —
 * {@code OrderStatus} 와 동일하게 DB 트리거를 두지 않고 애플리케이션 레벨에 일원화한다. 어느 Aggregate 도
 * 직접 변경하지 않는다 — 주문 상태 전이·재고 복원은 응답 호출 측({@code PaymentCallbackService})이 조율한다.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseTimeEntity {

    /** DB {@code pg_provider} 컬럼 길이 — {@link #initiate} 가 선검증해 DB 예외를 막는다. */
    static final int MAX_PG_PROVIDER_LENGTH = 20;
    /** DB {@code pg_transaction_id} 컬럼 길이 — {@link #initiate} 가 선검증해 DB 예외를 막는다. */
    static final int MAX_PG_TRANSACTION_ID_LENGTH = 100;
    /** DB {@code failure_reason} 컬럼 길이 — {@link #markFailed} 이 초과분을 잘라 DB 예외를 막는다. */
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
     * 결제 접수 — PG {@code request()} 응답 직후 {@link PaymentStatus#PENDING} 으로 생성한다.
     *
     * <p>대상 주문이 PENDING 상태인지·결제 가능한지는 호출 측({@code PaymentService})이 검증한다.
     *
     * @param order           결제 대상 주문
     * @param amount          결제 금액 (KRW, 양수)
     * @param method          결제 수단
     * @param pgProvider      PG 식별자 (예: {@code MOCK}) — blank 불가, {@value #MAX_PG_PROVIDER_LENGTH}자 이하
     * @param pgTransactionId PG 발급 거래 식별자 — blank 불가, {@value #MAX_PG_TRANSACTION_ID_LENGTH}자 이하
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
     * 결제 완료 확정 — PG 가 PAID 결과를 통보(웹훅)하거나 폴링 동기화 시 호출한다. {@code paidAt} 을 기록한다.
     *
     * <p>이미 종착 상태(PAID/FAILED/REFUNDED)인 결제에 호출하면 안 된다 — 호출 측({@code PaymentCallbackService})이
     * PENDING 인지 먼저 확인한다. 방어선으로 {@link PaymentStatus#canTransitionTo} 위반 시 {@link IllegalStateException}.
     */
    public void markPaid() {
        transitionTo(PaymentStatus.PAID);
        this.paidAt = Instant.now();
    }

    /**
     * 결제 실패 확정 — PG 가 FAILED 결과를 통보하거나 폴링 동기화 시 호출한다. {@code failureReason} 을
     * {@value #MAX_FAILURE_REASON_LENGTH}자 이내로 잘라 기록한다 (null 허용 — 사유 미상).
     */
    public void markFailed(String failureReason) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = truncate(failureReason);
    }

    /**
     * 환불 확정 — 관리자 환불 처리({@code AdminOrderService.refund}) 시 PG {@code refund()} 성공 후 호출한다.
     *
     * <p>PAID 가 아닌 결제(PENDING/FAILED/REFUNDED)에 호출하면 안 된다 — 호출 측이 PAID 인지 먼저 확인하며
     * 이미 REFUNDED 면 부수효과 없이 멱등 응답한다. 방어선으로 {@link PaymentStatus#canTransitionTo} 위반 시
     * {@link IllegalStateException}. {@code Payment} 는 환불 시각 컬럼을 두지 않는다 — PG 응답
     * ({@code RefundResponse.refundedAt})이 신뢰 원천이며 영속 상태로는 {@code REFUNDED} 만 남긴다.
     */
    public void markRefunded() {
        transitionTo(PaymentStatus.REFUNDED);
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
