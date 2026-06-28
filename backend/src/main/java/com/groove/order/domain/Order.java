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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 주문 — 회원/게스트 공용 테이블 (memberId XOR guestEmail).
 * 상태 변경은 changeStatus 단일 진입점, OrderItem 은 aggregate child (cascade ALL + orphanRemoval).
 */
@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

    static final int MAX_RECIPIENT_NAME_LENGTH = 50;
    static final int MAX_RECIPIENT_PHONE_LENGTH = 20;
    static final int MAX_ADDRESS_LENGTH = 500;
    static final int MAX_ADDRESS_DETAIL_LENGTH = 200;
    static final int MAX_ZIP_CODE_LENGTH = 20;

    /** PII 익명화 마스킹 라벨. */
    static final String ANONYMIZED_TEXT = "익명";

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

    /** 쿠폰 할인 금액 (원, 0 이상). payable = totalAmount − discountAmount, 쿠폰 미적용이면 0. */
    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** 반품 기한 anchor 의 결정적 기준. DELIVERED 전이에서만 기록. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    /** 전량 반품 완료 시점. 부분 반품만 있으면 null. */
    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "recipient_name", nullable = false, length = MAX_RECIPIENT_NAME_LENGTH)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = MAX_RECIPIENT_PHONE_LENGTH)
    private String recipientPhone;

    @Column(name = "address", nullable = false, length = MAX_ADDRESS_LENGTH)
    private String address;

    @Column(name = "address_detail", length = MAX_ADDRESS_DETAIL_LENGTH)
    private String addressDetail;

    @Column(name = "zip_code", nullable = false, length = MAX_ZIP_CODE_LENGTH)
    private String zipCode;

    @Column(name = "safe_packaging_requested", nullable = false)
    private boolean safePackagingRequested;

    /** PII 익명화 멱등 마커. */
    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @BatchSize(size = 50)
    @OrderBy("id ASC")
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    private Order(String orderNumber, Long memberId, String guestEmail, String guestPhone,
                  OrderShippingInfo shipping) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.guestEmail = guestEmail;
        this.guestPhone = guestPhone;
        this.recipientName = shipping.recipientName();
        this.recipientPhone = shipping.recipientPhone();
        this.address = shipping.address();
        this.addressDetail = shipping.addressDetail();
        this.zipCode = shipping.zipCode();
        this.safePackagingRequested = shipping.safePackagingRequested();
        this.status = OrderStatus.PENDING;
        this.totalAmount = 0L;
        this.discountAmount = 0L;
    }

    public static Order placeForMember(String orderNumber, Long memberId, OrderShippingInfo shipping) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber must not be blank");
        }
        if (memberId == null) {
            throw new InvalidOrderOwnershipException();
        }
        Objects.requireNonNull(shipping, "shipping must not be null");
        return new Order(orderNumber, memberId, null, null, shipping);
    }

    /** guestPhone 은 nullable. */
    public static Order placeForGuest(String orderNumber, String guestEmail, String guestPhone,
                                      OrderShippingInfo shipping) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber must not be blank");
        }
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new InvalidOrderOwnershipException();
        }
        Objects.requireNonNull(shipping, "shipping must not be null");
        return new Order(orderNumber, null, guestEmail, guestPhone, shipping);
    }

    /**
     * 상태 전이 단일 진입점. 모든 전이(파이프라인·관리자 강제·시드)가 이곳을 거치므로 deliveredAt 이 배송 행
     * 유무와 무관하게 항상 채워진다(반품 기한 anchor 결정성). 불법 전이면 예외.
     */
    public void changeStatus(OrderStatus next, String reason, Instant now) {
        Objects.requireNonNull(next, "next status must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(status, next);
        }
        this.status = next;
        if (next == OrderStatus.PAID) {
            this.paidAt = now;
        } else if (next == OrderStatus.DELIVERED) {
            this.deliveredAt = now;
        } else if (next == OrderStatus.CANCELLED) {
            this.cancelledAt = now;
            this.cancelledReason = reason;
        }
    }

    /** 합법 전이일 때만 전진하고 아니면 false. */
    public boolean advanceTo(OrderStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        changeStatus(target, null, now);
        return true;
    }

    /** 쿠폰 할인 적용. PENDING 한정, 0 ≤ amount ≤ totalAmount. */
    public void applyDiscount(long amount) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(status, OrderStatus.PENDING);
        }
        if (amount < 0) {
            throw new IllegalArgumentException("discount amount must be non-negative: " + amount);
        }
        if (amount > totalAmount) {
            throw new IllegalArgumentException(
                    "discount amount " + amount + " exceeds totalAmount " + totalAmount);
        }
        this.discountAmount = amount;
    }

    /** 멱등 — 이미 기록돼 있으면 무시. */
    public void recordTrackingNumber(String trackingNumber) {
        if (this.trackingNumber != null) {
            return;
        }
        this.trackingNumber = trackingNumber;
    }

    /** PII(수령인/주소/게스트) 마스킹, address_detail 은 NULL. 멱등. */
    public void anonymizePii(Instant now) {
        if (this.anonymizedAt != null) {
            return;
        }
        this.recipientName = ANONYMIZED_TEXT;
        this.recipientPhone = ANONYMIZED_TEXT;
        this.address = ANONYMIZED_TEXT;
        this.addressDetail = null;
        this.zipCode = ANONYMIZED_TEXT;
        this.guestEmail = null;
        this.guestPhone = null;
        this.anonymizedAt = now;
    }

    public boolean isAnonymized() {
        return this.anonymizedAt != null;
    }

    /** 멱등 — 이미 찍혀 있으면 첫 값 보존. */
    public void markReturned(Instant now) {
        if (this.returnedAt != null) {
            return;
        }
        this.returnedAt = now;
    }

    public boolean isReturned() {
        return this.returnedAt != null;
    }

    /** 항목 추가 + totalAmount 누적. */
    public void addItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        // 합계를 먼저 계산해 오버플로 시 컬렉션을 건드리기 전에 실패 — 항목만 추가되고 합계는 옛값인 불일치 방지.
        long nextTotal = Math.addExact(this.totalAmount, item.getSubtotal());
        item.attachTo(this);
        items.add(item);
        this.totalAmount = nextTotal;
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

    public long getDiscountAmount() {
        return discountAmount;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    /** 실제 청구 금액 = totalAmount − discountAmount. */
    public long getPayableAmount() {
        return totalAmount - discountAmount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getAnonymizedAt() {
        return anonymizedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    /** 주문 시점 배송지 스냅샷. */
    public OrderShippingInfo getShippingInfo() {
        return new OrderShippingInfo(recipientName, recipientPhone, address, addressDetail, zipCode,
                safePackagingRequested);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isGuestOrder() {
        return memberId == null;
    }
}
