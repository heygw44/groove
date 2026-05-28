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

    /** DB {@code recipient_name} 컬럼 길이 — {@link OrderShippingInfo} 가 선검증한다. */
    static final int MAX_RECIPIENT_NAME_LENGTH = 50;
    /** DB {@code recipient_phone} 컬럼 길이 — {@link OrderShippingInfo} 가 선검증한다. */
    static final int MAX_RECIPIENT_PHONE_LENGTH = 20;
    /** DB {@code address} 컬럼 길이 — {@link OrderShippingInfo} 가 선검증한다. */
    static final int MAX_ADDRESS_LENGTH = 500;
    /** DB {@code address_detail} 컬럼 길이 — {@link OrderShippingInfo} 가 선검증한다. */
    static final int MAX_ADDRESS_DETAIL_LENGTH = 200;
    /** DB {@code zip_code} 컬럼 길이 — {@link OrderShippingInfo} 가 선검증한다. */
    static final int MAX_ZIP_CODE_LENGTH = 20;

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

    /**
     * 쿠폰 할인 금액 (원, 0 이상). {@code payable = totalAmount − discountAmount} 의 차감분.
     *
     * <p>쿠폰 미적용 주문에서는 {@code 0} 으로 유지된다. DB CHECK ({@code ck_orders_discount_within_total},
     * V15) 가 {@code 0 ≤ discount_amount ≤ total_amount} 를 2차 방어하지만, 1차 방어는 도메인
     * {@link #applyDiscount(long)} 가 한다 — 적용 시점 검증으로 CHECK 위반이 DB 까지 닿지 않게 한다.
     *
     * <p>설계상 PENDING 상태에서만 변경된다 (적용/취소복원 모두 PENDING 진입 시점). 그래서 가드 변경자
     * {@link #applyDiscount(long)} 가 status 확인을 함께 수행한다 — totalAmount 가 확정되기 전(addItem 도중)에
     * 적용되면 CHECK 위반이 가능하므로 호출자({@code OrderService.place})는 totalAmount 확정 후 호출한다.
     */
    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

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

    /**
     * 페이징 쿼리({@code findByMemberId} 등)에서 컬렉션 fetch join 시 발생하는 인메모리 페이지네이션을
     * 회피하기 위해 {@link BatchSize} 를 둔다 — Hibernate 가 Order 페이지를 SQL LIMIT 으로 가져온 뒤
     * items 를 IN 쿼리로 일괄 로드한다 (N+1 도 함께 방지).
     *
     * <p>{@link OrderBy}("id ASC") 가 IN 쿼리에 명시적 정렬을 부여한다 — auto-increment PK 기준
     * 삽입 순서를 보장해 {@code OrderSummaryResponse.representativeAlbumTitle} 이 결정적이도록 한다.
     */
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

    /**
     * 회원 주문 생성. 초기 상태 PENDING. {@code shipping} 은 배송지 스냅샷 — 결제 완료 시 그대로 배송 행으로 복사된다.
     */
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

    /**
     * 게스트 주문 생성. 초기 상태 PENDING. {@code guestPhone} 은 nullable, {@code shipping} 은 배송지 스냅샷.
     */
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
     * 쿠폰 할인 적용 — PENDING 상태에서 {@code totalAmount} 확정 후 단 한 번 호출되는 것을 전제한다.
     *
     * <p>가드:
     * <ul>
     *   <li>{@code status != PENDING} → {@link IllegalStateTransitionException} (현재→PENDING 가짜 전이로 의도 표현).
     *       결제 이후 할인 변경을 금지해 결제 금액과 주문 금액의 정합성을 지킨다.</li>
     *   <li>{@code amount < 0} 또는 {@code amount > totalAmount} → {@link IllegalArgumentException} —
     *       DB CHECK ({@code ck_orders_discount_within_total}, V15) 위반을 사전 차단한다.</li>
     * </ul>
     *
     * <p>중복 호출은 막지 않는다 — {@code OrderService.place} 는 라이프사이클상 1회만 호출하며,
     * 이상 동작이 발생해도 마지막 값이 CHECK 안에 있으면 데이터 정합성은 깨지지 않는다.
     */
    public void applyDiscount(long amount) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(status, status);
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

    public long getDiscountAmount() {
        return discountAmount;
    }

    /**
     * 실제 청구 금액 — {@code totalAmount − discountAmount}. {@link com.groove.coupon.domain.Coupon}
     * 의 {@code discount ≤ subtotal} 불변식과 {@link #applyDiscount} 의 가드, V15 의 CHECK 가
     * 함께 {@code ≥ 0} 을 보장한다. {@code PaymentService.requestPayment} 가 PG 청구액으로 사용한다.
     */
    public long getPayableAmount() {
        return totalAmount - discountAmount;
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

    /**
     * 주문 시점에 캡처된 배송지 스냅샷. 결제 완료 후 {@code shipping} 행을 만들 때 그대로 복사된다.
     */
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
