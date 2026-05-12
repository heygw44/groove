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
 * (ERD §4.11). 본 이슈(#W7-3)는 {@link #initiate} 까지만 다룬다 — PAID/FAILED 전이와 {@code paidAt}/
 * {@code failureReason} 기록은 #W7-4 웹훅/폴링에서 추가한다.
 *
 * <p>상태 전이 규칙은 {@link PaymentStatus#canTransitionTo(PaymentStatus)} 가 판정한다 —
 * {@code OrderStatus} 와 동일하게 DB 트리거를 두지 않고 애플리케이션 레벨에 일원화한다.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseTimeEntity {

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

    @Column(name = "pg_provider", nullable = false, length = 20)
    private String pgProvider;

    @Column(name = "pg_transaction_id", length = 100)
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
     * @param pgProvider      PG 식별자 (예: {@code MOCK}) — blank 불가
     * @param pgTransactionId PG 발급 거래 식별자 — blank 불가
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
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
            throw new IllegalArgumentException("pgTransactionId must not be blank");
        }
        return new Payment(order, amount, method, pgProvider, pgTransactionId);
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
