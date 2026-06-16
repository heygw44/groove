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
 * 배송.
 *
 * 주문당 배송 1건 — order_id UNIQUE. prepare 로 PREPARING 상태로 생성하고, 자동 진행 스케줄러가
 * markShipped → markDelivered 로 한 단계씩 밀어준다. 배송지 정보는 주문 시점 OrderShippingInfo 스냅샷을 그대로 복사한다.
 *
 * 상태 전이 규칙은 ShippingStatus.canTransitionTo 가 판정한다(위반 시 IllegalStateException).
 * 발송 전 취소·환불 시에는 cancel 로 CANCELLED 로 전이시킨다.
 */
@Entity
@Table(name = "shipping")
public class Shipping extends BaseTimeEntity {

    /** DB tracking_number 컬럼 길이 — prepare 가 선검증해 DB 예외를 막는다. */
    static final int MAX_TRACKING_NUMBER_LENGTH = 50;

    /** PII 익명화 시 텍스트 필드에 채우는 마스킹 라벨. */
    static final String ANONYMIZED_TEXT = "익명";

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

    /** 배송 PII 익명화 시점 — 배치 대상 선별과 멱등 마커를 겸한다. */
    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

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
     * 배송 준비 시작 — 초기 상태 PREPARING. 운송장 번호는 blank 불가, {@value #MAX_TRACKING_NUMBER_LENGTH}자 이하.
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
     * 발송 처리 — SHIPPED 로 전이하고 shippedAt 을 기록한다.
     */
    public void markShipped() {
        transitionTo(ShippingStatus.SHIPPED);
        this.shippedAt = Instant.now();
    }

    /**
     * 배송 완료 처리 — DELIVERED 로 전이하고 deliveredAt 을 기록한다.
     */
    public void markDelivered() {
        transitionTo(ShippingStatus.DELIVERED);
        this.deliveredAt = Instant.now();
    }

    /**
     * 배송 취소 — 종착 상태(CANCELLED)로 전이한다. DELIVERED·이미 CANCELLED 에서는 불법 전이로 IllegalStateException.
     */
    public void cancel() {
        transitionTo(ShippingStatus.CANCELLED);
    }

    private void transitionTo(ShippingStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 배송 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
    }

    /**
     * 배송 PII 익명화 — 수령인/주소 PII 를 마스킹한다. address_detail 은 NULL 로 비운다. 운송장 번호·상태·시각 등 비-PII 는 보존.
     * 멱등: 이미 익명화됐으면(anonymized_at != null) no-op.
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
        this.anonymizedAt = now;
    }

    /** 이미 PII 익명화된 배송인지 여부. */
    public boolean isAnonymized() {
        return this.anonymizedAt != null;
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

    public Instant getAnonymizedAt() {
        return anonymizedAt;
    }
}
