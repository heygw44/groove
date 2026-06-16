package com.groove.cart.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.cart.exception.CartQuantityLimitExceededException;
import com.groove.common.persistence.BaseTimeEntity;
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
 * 장바구니 항목. (cart_id, album_id) UNIQUE — 동일 상품 중복 행은 DB 에서 거부된다.
 */
@Entity
@Table(name = "cart_item")
public class CartItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected CartItem() {
    }

    private CartItem(Cart cart, Album album, int quantity) {
        this.cart = cart;
        this.album = album;
        this.quantity = quantity;
    }

    /** 정적 팩토리. quantity 의 [1, MAX] 범위를 도메인에서 검증한다. */
    public static CartItem create(Cart cart, Album album, int quantity) {
        validateQuantity(quantity);
        return new CartItem(cart, album, quantity);
    }

    public void accumulate(int delta) {
        long next = (long) this.quantity + delta;
        if (next > Cart.MAX_ITEM_QUANTITY) {
            throw new CartQuantityLimitExceededException();
        }
        validateQuantity((int) next);
        this.quantity = (int) next;
    }

    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Album getAlbum() {
        return album;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getSubtotal() {
        return (long) album.getPrice() * quantity;
    }

    private static void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > Cart.MAX_ITEM_QUANTITY) {
            throw new CartQuantityLimitExceededException();
        }
    }
}
