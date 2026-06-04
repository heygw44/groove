package com.groove.shipping.domain;

import com.groove.common.persistence.BaseTimeEntity;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderShippingInfo;
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
 * 배송 (ERD §4.13, glossary §3.6).
 *
 * <p>주문당 배송 1건 — {@code order_id} UNIQUE. 결제 완료 이벤트({@code OrderPaidEvent}) 의 AFTER_COMMIT
 * 리스너가 {@link #prepare} 로 {@link ShippingStatus#PREPARING} 상태로 생성하고, 시연용 자동 진행
 * 스케줄러가 {@link #markShipped()} → {@link #markDelivered()} 로 한 단계씩 밀어준다. 배송지 정보는 주문
 * 시점에 캡처된 {@link OrderShippingInfo} 스냅샷을 그대로 복사한다 — 주문이 사후에 바뀌어도 발송된 배송에는 영향이 없다.
 *
 * <p>상태 전이 규칙은 {@link ShippingStatus#canTransitionTo(ShippingStatus)} 가 판정한다 —
 * {@code Payment} 와 동일하게 DB 트리거를 두지 않고 애플리케이션 레벨에 일원화하며, 위반 시 {@link IllegalStateException}.
 */
@Entity
@Table(name = "shipping")
public class Shipping extends BaseTimeEntity {

    /** DB {@code tracking_number} 컬럼 길이 — {@link #prepare} 가 선검증해 DB 예외를 막는다. */
    static final int MAX_TRACKING_NUMBER_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "tracking_number", nullable = false, length = MAX_TRACKING_NUMBER_LENGTH, unique = true)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShippingStatus status;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "address_detail", length = 200)
    private String addressDetail;

    @Column(name = "zip_code", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "safe_packaging_requested", nullable = false)
    private boolean safePackagingRequested;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected Shipping() {
    }

    private Shipping(Order order, String trackingNumber, OrderShippingInfo info) {
        this.order = order;
        this.trackingNumber = trackingNumber;
        this.status = ShippingStatus.PREPARING;
        this.recipientName = info.recipientName();
        this.recipientPhone = info.recipientPhone();
        this.address = info.address();
        this.addressDetail = info.addressDetail();
        this.zipCode = info.zipCode();
        this.safePackagingRequested = info.safePackagingRequested();
    }

    /**
     * 배송 준비 시작 — 결제 완료 후 호출한다. 초기 상태 {@link ShippingStatus#PREPARING}.
     *
     * @param order          배송 대상 주문 (1:1)
     * @param shippingInfo   주문 시점에 캡처된 배송지 스냅샷
     * @param trackingNumber 운송장 번호 — blank 불가, {@value #MAX_TRACKING_NUMBER_LENGTH}자 이하
     */
    public static Shipping prepare(Order order, OrderShippingInfo shippingInfo, String trackingNumber) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(shippingInfo, "shippingInfo must not be null");
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
        if (trackingNumber.length() > MAX_TRACKING_NUMBER_LENGTH) {
            throw new IllegalArgumentException("trackingNumber length must be <= " + MAX_TRACKING_NUMBER_LENGTH);
        }
        return new Shipping(order, trackingNumber, shippingInfo);
    }

    /**
     * 발송 처리 — 자동 진행 스케줄러가 {@code PREPARING} 인 배송을 일정 시간 경과 후 호출한다. {@code shippedAt} 을 기록한다.
     */
    public void markShipped() {
        transitionTo(ShippingStatus.SHIPPED);
        this.shippedAt = Instant.now();
    }

    /**
     * 배송 완료 처리 — 자동 진행 스케줄러가 {@code SHIPPED} 인 배송을 일정 시간 경과 후 호출한다. {@code deliveredAt} 을 기록한다.
     */
    public void markDelivered() {
        transitionTo(ShippingStatus.DELIVERED);
        this.deliveredAt = Instant.now();
    }

    private void transitionTo(ShippingStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 배송 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShippingStatus getStatus() {
        return status;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public String getZipCode() {
        return zipCode;
    }

    public boolean isSafePackagingRequested() {
        return safePackagingRequested;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
