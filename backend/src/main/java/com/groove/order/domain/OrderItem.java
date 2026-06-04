package com.groove.order.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.common.persistence.BaseTimeEntity;
import com.groove.order.exception.InvalidOrderItemException;
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
 * 주문 항목 (ERD §4.10).
 *
 * <p>가격(unit_price) 과 앨범 제목(album_title_snapshot) 을 주문 시점 값으로 스냅샷한다 —
 * 이후 album 의 가격/제목이 변경되어도 주문 이력은 그대로 보존된다.
 *
 * <p>{@code quantity > 0}, {@code unit_price >= 0} 은 DB CHECK 제약과 도메인 메서드의
 * 이중 방어선이다.
 */
@Entity
@Table(name = "order_item")
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "album_title_snapshot", nullable = false, length = 300)
    private String albumTitleSnapshot;

    protected OrderItem() {
    }

    private OrderItem(Album album, int quantity, long unitPrice, String albumTitleSnapshot) {
        this.album = album;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.albumTitleSnapshot = albumTitleSnapshot;
    }

    /**
     * 정적 팩토리. album 의 현재 가격/제목을 스냅샷으로 복사한다.
     *
     * <p>{@code Order.addItem} 호출 전까지 order 연관은 비어 있고, addItem 시점에
     * {@link #attachTo(Order)} 를 통해 주입된다.
     *
     * <p>호출 측({@code OrderService}, #W6-3) 이 album.status == SELLING 검증을 끝낸 상태로
     * 전달한다고 가정한다 — Cart 패턴과 동일하게 도메인은 구매 가능 여부를 재검증하지 않는다.
     */
    public static OrderItem create(Album album, int quantity) {
        if (album == null) {
            throw new InvalidOrderItemException("album must not be null");
        }
        validateQuantity(quantity);
        long price = album.getPrice();
        validateUnitPrice(price);
        return new OrderItem(album, quantity, price, album.getTitle());
    }

    void attachTo(Order order) {
        this.order = order;
    }

    public long getSubtotal() {
        return unitPrice * quantity;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Album getAlbum() {
        return album;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public String getAlbumTitleSnapshot() {
        return albumTitleSnapshot;
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidOrderItemException("quantity must be positive: " + quantity);
        }
    }

    private static void validateUnitPrice(long unitPrice) {
        if (unitPrice < 0) {
            throw new InvalidOrderItemException("unit_price must be non-negative: " + unitPrice);
        }
    }
}
