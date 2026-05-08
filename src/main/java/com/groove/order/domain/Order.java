package com.groove.order.domain;

import com.groove.common.persistence.BaseTimeEntity;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.InvalidOrderOwnershipException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 주문 (ERD §4.9, glossary §3.4).
 *
 * <p>회원/게스트 주문을 동일 테이블에서 표현한다. {@code memberId} XOR {@code guestEmail} —
 * 정확히 한 쪽만 채워져야 하며 이 규칙은 도메인 정적 팩토리에서 검증한다 (DB 제약은 없음, ERD §4.9).
 *
 * <p>상태 변경은 {@link #changeStatus(OrderStatus, String)} 단일 진입점만 허용한다.
 * 합법 전이는 {@link OrderStatus#canTransitionTo(OrderStatus)} 가 판정하며 위반 시
 * {@link IllegalStateTransitionException} (HTTP 409) 이다.
 *
 * <p>OrderItem 은 aggregate child — {@code cascade=ALL + orphanRemoval=true} 로
 * Order 를 통해서만 변경된다. {@code totalAmount} 는 {@link #addItem(OrderItem)} 호출 시 갱신된다.
 *
 * <p>본 이슈(#42) 는 모델 + 상태 머신만 다룬다. orderNumber 발급기, 재고 차감, API 진입점은
 * 후속 이슈(#W6-3) 에서 추가된다 — 따라서 정적 팩토리는 외부에서 채번된 orderNumber 를 받는다.
 */
@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 30, unique = true)
    private String orderNumber;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "guest_email", length = 255)
    private String guestEmail;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    private Order(String orderNumber, Long memberId, String guestEmail, String guestPhone) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.guestEmail = guestEmail;
        this.guestPhone = guestPhone;
        this.status = OrderStatus.PENDING;
        this.totalAmount = 0L;
    }

    /**
     * 회원 주문 생성. 초기 상태 PENDING.
     */
    public static Order placeForMember(String orderNumber, Long memberId) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber must not be blank");
        }
        if (memberId == null) {
            throw new InvalidOrderOwnershipException();
        }
        return new Order(orderNumber, memberId, null, null);
    }

    /**
     * 게스트 주문 생성. 초기 상태 PENDING. {@code guestPhone} 은 nullable.
     */
    public static Order placeForGuest(String orderNumber, String guestEmail, String guestPhone) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber must not be blank");
        }
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new InvalidOrderOwnershipException();
        }
        return new Order(orderNumber, null, guestEmail, guestPhone);
    }

    /**
     * 상태 전이 단일 진입점.
     *
     * <ul>
     *   <li>{@link OrderStatus#canTransitionTo} 가 false → {@link IllegalStateTransitionException}.</li>
     *   <li>PAID 진입 시 {@code paidAt = now}.</li>
     *   <li>CANCELLED 진입 시 {@code cancelledAt = now}, {@code cancelledReason = reason}.</li>
     *   <li>그 외 전이는 {@code reason} 을 무시한다.</li>
     * </ul>
     */
    public void changeStatus(OrderStatus next, String reason) {
        Objects.requireNonNull(next, "next status must not be null");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(status, next);
        }
        this.status = next;
        if (next == OrderStatus.PAID) {
            this.paidAt = Instant.now();
        } else if (next == OrderStatus.CANCELLED) {
            this.cancelledAt = Instant.now();
            this.cancelledReason = reason;
        }
    }

    /**
     * 항목 추가 + totalAmount 누적.
     */
    public void addItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        item.attachTo(this);
        items.add(item);
        this.totalAmount += item.getSubtotal();
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isGuestOrder() {
        return memberId == null;
    }
}
