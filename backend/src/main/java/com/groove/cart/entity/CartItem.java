package com.groove.cart.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장바구니에 담긴 상품 한 줄. 회원의 장바구니당 상품 하나에 행 하나만 존재한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "cart_item",
		uniqueConstraints = @UniqueConstraint(name = "uk_cart_item", columnNames = {"cart_id", "product_id"}))
@Check(name = "chk_cart_item_quantity", constraints = "quantity > 0")
public class CartItem extends BaseTimeEntity {

	public static final int MAX_QUANTITY = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_cart"))
	private Cart cart;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_product"))
	private Product product;

	@Column(nullable = false)
	@ColumnDefault("1")
	private int quantity;

	@Builder(access = PRIVATE)
	private CartItem(Cart cart, Product product, int quantity) {
		this.cart = cart;
		this.product = product;
		this.quantity = quantity;
	}

	public static CartItem create(Cart cart, Product product, int quantity) {
		validateQuantity(quantity);
		return CartItem.builder()
				.cart(cart)
				.product(product)
				.quantity(quantity)
				.build();
	}

	public void addQuantity(int amount) {
		changeQuantity(this.quantity + amount);
	}

	public void changeQuantity(int quantity) {
		validateQuantity(quantity);
		this.quantity = quantity;
	}

	private static void validateQuantity(int quantity) {
		if (quantity <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (quantity > MAX_QUANTITY) {
			throw new BusinessException(ErrorCode.CART_QUANTITY_EXCEEDED);
		}
	}
}
