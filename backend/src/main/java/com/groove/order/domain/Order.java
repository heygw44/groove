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
 * 회원/게스트 주문을 동일 테이블에서 표현한다. memberId XOR guestEmail — 정확히 한 쪽만 채워져야 하며 이 규칙은
 * 도메인 정적 팩토리에서 검증한다 (DB 제약은 없음, ERD §4.9).
 *
 * 상태 변경은 changeStatus(OrderStatus, String) 단일 진입점만 허용한다. 합법 전이는
 * OrderStatus.canTransitionTo 가 판정하며 위반 시 IllegalStateTransitionException (HTTP 409).
 *
 * OrderItem 은 aggregate child — cascade=ALL + orphanRemoval=true 로 Order 를 통해서만 변경되고,
 * totalAmount 는 addItem 호출 시 갱신된다.
 *
 * 본 이슈(#42)는 모델 + 상태 머신만 다룬다. orderNumber 발급기·재고 차감·API 진입점은 후속 이슈(#W6-3)에서 추가되며,
 * 그래서 정적 팩토리는 외부에서 채번된 orderNumber 를 받는다.
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

    /** PII 익명화 시 텍스트 필드에 채우는 마스킹 라벨 (#170 Part B). 모든 PII 컬럼 길이 안에 들어간다. */
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

    /**
     * 쿠폰 할인 금액 (원, 0 이상). payable = totalAmount − discountAmount 의 차감분이며 쿠폰 미적용 주문에서는 0.
     *
     * DB CHECK (ck_orders_discount_within_total, V15) 가 0 ≤ discount_amount ≤ total_amount 를 2차 방어하지만,
     * 1차 방어는 도메인 applyDiscount 가 적용 시점 검증으로 CHECK 위반을 DB 까지 닿지 않게 막는다.
     *
     * 설계상 PENDING 상태에서만 변경된다(적용/취소복원 모두 PENDING 진입 시점)라 가드 변경자 applyDiscount 가 status
     * 확인을 함께 수행한다. totalAmount 확정 전(addItem 도중)에 적용되면 CHECK 위반이 가능하므로 호출자
     * (OrderService.place)는 totalAmount 확정 후 호출한다.
     */
    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    /**
     * 발급된 운송장 번호 (이슈 #116). 결제 완료 후 shipping 모듈의 OrderPaidOutboxHandler 가 배송 생성 직후
     * 기록하며, 결제 전(배송 미생성)에는 null. OrderResponse 가 그대로 노출해 프론트가 별도 매핑 없이
     * GET /shippings/{trackingNumber} 로 배송 추적하게 한다 (라이브 배송 상태는 그 엔드포인트가 담당).
     */
    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    /**
     * 전량 반품 완료 시점 (#239 역물류). 배송완료(DELIVERED)/완료(COMPLETED) 주문이 반품(claim)으로 모든
     * OrderItem 의 누적 반품 수량이 주문 수량과 같아지면 {@code ClaimService.completeRefund} 가 찍는 마커다.
     * OrderStatus 를 건드리지 않고("상태 폭발" 회피) "배송된 사실"은 유지한 채 전량 환불 여부만 표식한다 — 부분 반품만
     * 있으면 null 로 남고, Claim aggregate 가 부분 반품의 진실 원천이다. 멱등 마커로도 쓰인다(이미 찍혀 있으면 보존).
     */
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

    /**
     * 주문 PII 익명화 시점 (#170 Part B). 배송완료 후 보존기간이 지나면 {@code OrderPiiAnonymizationScheduler}
     * 가 수령인/주소/게스트 PII 를 마스킹하고 이 시각을 찍는다 — 다음 주기에서 제외하는 멱등 마커다.
     */
    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    /**
     * 페이징 쿼리(findByMemberId 등)에서 컬렉션 fetch join 시 발생하는 인메모리 페이지네이션을 회피하려고
     * BatchSize 를 둔다 — Hibernate 가 Order 페이지를 SQL LIMIT 으로 가져온 뒤 items 를 IN 쿼리로 일괄 로드한다
     * (N+1 도 함께 방지). OrderBy("id ASC") 가 IN 쿼리에 명시적 정렬을 부여해 auto-increment PK 기준 삽입 순서를
     * 보장하므로 OrderSummaryResponse.representativeAlbumTitle 이 결정적이다.
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

    /** 회원 주문 생성. 초기 상태 PENDING. shipping 은 배송지 스냅샷 — 결제 완료 시 그대로 배송 행으로 복사된다. */
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

    /** 게스트 주문 생성. 초기 상태 PENDING. guestPhone 은 nullable, shipping 은 배송지 스냅샷. */
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
     * 상태 전이 단일 진입점. OrderStatus.canTransitionTo 가 false 면 IllegalStateTransitionException.
     * PAID 진입 시 paidAt = now, CANCELLED 진입 시 cancelledAt = now·cancelledReason = reason, 그 외 전이는 reason 을
     * 무시한다.
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
     * 합법 전이일 때만 상태를 전진시키고 아니면 무해하게 무시한다(멱등·발산 방어). changeStatus 가 불법 전이를
     * IllegalStateTransitionException 으로 거부하는 엄격한 단일 진입점이라면, 이 메서드는 배송 자동 진행(이슈 #161)처럼
     * "이미 도달했거나 경로를 벗어난" 주문을 만나도 예외 없이 넘어가야 하는 호출자를 위한 관대한 변형이다. reason 이
     * 의미를
     * 갖는 전이(CANCELLED)에는 쓰지 않는다.
     *
     * @return 전이가 일어났으면 true, 불법이라 건너뛰었으면 false
     */
    public boolean advanceTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        changeStatus(target, null);
        return true;
    }

    /**
     * 쿠폰 할인 적용 — PENDING 상태에서 totalAmount 확정 후 단 한 번 호출되는 것을 전제한다.
     *
     * 가드: status != PENDING 이면 IllegalStateTransitionException(status → PENDING) — 두 번째 인자에 PENDING 을 둬
     * "PENDING 으로 되돌아가야 할인이 가능하다"는 의도를 메시지로 드러내며, 결제 이후 할인 변경을 금지해 결제 금액과
     * 주문
     * 금액의 정합성을 지킨다. amount < 0 또는 amount > totalAmount 면 IllegalArgumentException — DB CHECK
     * (ck_orders_discount_within_total, V15) 위반을 사전 차단한다.
     *
     * 중복 호출은 막지 않는다 — OrderService.place 는 라이프사이클상 1회만 호출하며, 이상 동작이 발생해도 마지막 값이
     * CHECK 안에 있으면 데이터 정합성은 깨지지 않는다.
     */
    public void applyDiscount(long amount) {
        if (status != OrderStatus.PENDING) {
            // 두 번째 인자에 PENDING — 메시지가 "PAID -> PENDING" 으로 드러나 의도 명확.
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

    /**
     * 배송 생성 시 발급된 운송장 번호를 주문에 기록한다 (이슈 #116). shipping 모듈의 OrderPaidOutboxHandler 가
     * 결제 완료 후 배송 생성 직후 호출한다 — 쓰기 방향이 shipping→order 라 모듈 의존(shipping 이 order 를 안다)을 깨지
     * 않으며, 주문 상세 응답이 운송장 번호를 노출해 프론트가 별도 매핑 없이 배송 추적을 연결할 수 있게 한다.
     * 멱등 — 이미 기록돼 있으면 무시한다(중복 이벤트·경합 시 첫 값 보존).
     */
    public void recordTrackingNumber(String trackingNumber) {
        if (this.trackingNumber != null) {
            return;
        }
        this.trackingNumber = trackingNumber;
    }

    /**
     * 주문 PII 익명화 (#170 Part B, GDPR/개인정보 파기·익명화 의무).
     *
     * 배송완료 후 보존기간이 지난 주문의 수령인/주소/게스트 PII 를 마스킹한다 — 회원/게스트 주문 모두 동일 적용
     * (게스트 주문은 이 배치가 PII 를 비우는 유일한 경로). 주문번호·금액·상태·시각 등 비-PII 는 회계/이력 목적으로
     * 보존하며, address_detail 은 원래 NULL 일 수 있어 NULL 로 비운다.
     * 멱등: 이미 익명화됐으면(anonymized_at != null) no-op — 배치 재실행·중복 호출에 안전하다.
     */
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

    /** 이미 PII 익명화된 주문인지 여부 (#170 Part B). */
    public boolean isAnonymized() {
        return this.anonymizedAt != null;
    }

    /**
     * 전량 반품 완료 마커를 찍는다 (#239) — 모든 OrderItem 이 반품(claim) 환불되어 결제 전액이 환불됐을 때
     * {@code ClaimService.completeRefund} 가 호출한다. OrderStatus 는 DELIVERED/COMPLETED 로 유지해 "배송된 사실"을
     * 보존하고, 환불 여부만 별도 마커로 표식한다. 멱등 — 이미 찍혀 있으면 첫 값을 보존한다.
     */
    public void markReturned(Instant now) {
        if (this.returnedAt != null) {
            return;
        }
        this.returnedAt = now;
    }

    /** 전량 반품으로 환불 완료된 주문인지 여부 (#239). 부분 반품만 있거나 미반품이면 {@code null}. */
    public boolean isReturned() {
        return this.returnedAt != null;
    }

    /** 항목 추가 + totalAmount 누적. */
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

    /** 발급된 운송장 번호 — 배송 생성 전(결제 완료 전)에는 {@code null}. */
    public String getTrackingNumber() {
        return trackingNumber;
    }

    /**
     * 실제 청구 금액 — totalAmount − discountAmount. Coupon 의 discount ≤ subtotal 불변식과 applyDiscount 의 가드,
     * V15 의 CHECK 가 함께 ≥ 0 을 보장한다. PaymentService.requestPayment 가 PG 청구액으로 사용한다.
     */
    public long getPayableAmount() {
        return totalAmount - discountAmount;
    }

    public Instant getPaidAt() {
        return paidAt;
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

    /** 전량 반품 완료 시점 (#239) — 부분 반품만 있거나 미반품이면 {@code null}. */
    public Instant getReturnedAt() {
        return returnedAt;
    }

    /** 주문 시점에 캡처된 배송지 스냅샷. 결제 완료 후 shipping 행을 만들 때 그대로 복사된다. */
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
