package com.groove.claim.domain;

import com.groove.common.persistence.BaseTimeEntity;
import com.groove.order.domain.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 반품 항목 — 한 반품(claim)에서 특정 OrderItem 을 몇 개 반품하는지 나타내는 aggregate child. 단가는 주문 시점
 * 값으로 스냅샷(unitPriceSnapshot)한다.
 */
@Entity
@Table(name = "claim_item")
public class ClaimItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_snapshot", nullable = false)
    private long unitPriceSnapshot;

    protected ClaimItem() {
    }

    private ClaimItem(OrderItem orderItem, int quantity, long unitPriceSnapshot) {
        this.orderItem = orderItem;
        this.quantity = quantity;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    /** 정적 팩토리. 반품할 OrderItem 과 수량을 받아 단가를 주문 시점 값으로 스냅샷한다. */
    public static ClaimItem of(OrderItem orderItem, int quantity) {
        if (orderItem == null) {
            throw new IllegalArgumentException("orderItem must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive: " + quantity);
        }
        return new ClaimItem(orderItem, quantity, orderItem.getUnitPrice());
    }

    void attachTo(Claim claim) {
        this.claim = claim;
    }

    /** 이 항목의 반품 정가 합 — unitPriceSnapshot × quantity. 오버플로는 Math.multiplyExact 로 ArithmeticException. */
    public long getGross() {
        return Math.multiplyExact(unitPriceSnapshot, (long) quantity);
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }
}
