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
 * 반품 항목 (#239) — 한 반품(claim)에서 특정 {@link OrderItem} 을 몇 개 반품하는지 나타내는 aggregate child.
 *
 * <p>부분 반품을 지원하려고 OrderItem 단위로 수량을 따로 들고, 환불액 계산의 기준이 되는 단가를 주문 시점 값으로
 * 스냅샷({@code unitPriceSnapshot})한다 — OrderItem.unitPrice 와 동일하지만, 환불 회계가 OrderItem 의 사후 변경에
 * 영향받지 않도록 복사한다.
 *
 * <p>{@code Claim} 을 통해서만 생성/변경된다(aggregate child). {@code quantity > 0} 은 DB CHECK 와 도메인 검증의
 * 이중 방어선이다.
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

    /**
     * 정적 팩토리. 반품할 OrderItem 과 수량을 받아 단가를 주문 시점 값으로 스냅샷한다.
     *
     * <p>수량 양수·주문 잔여 수량 이내 검증은 호출 측({@code ClaimService})이 끝낸 상태로 전달한다고 가정한다 —
     * 도메인은 최소 방어선만 둔다.
     */
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

    /**
     * 이 항목의 반품 정가 합 — {@code unitPriceSnapshot × quantity}. 할인 미반영(claim 환불액 비례 배분의 기준 분자).
     * 곱셈 오버플로는 {@link Math#multiplyExact}로 조용한 음수 wrap 대신 {@link ArithmeticException}으로 드러낸다.
     */
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
