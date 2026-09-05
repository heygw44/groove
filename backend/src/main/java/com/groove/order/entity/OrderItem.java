package com.groove.order.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;

import com.groove.global.common.BaseTimeEntity;
import com.groove.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 항목. 상품명·가격은 주문 시점 스냅샷으로 보관한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "order_item",
		indexes = {
			@Index(name = "idx_order_item_order", columnList = "order_id"),
			@Index(name = "idx_order_item_product", columnList = "product_id")
		})
public class OrderItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
	private Order order;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_product"))
	private Product product;

	@Column(name = "product_name_snapshot", nullable = false, length = 200)
	private String productName;

	@Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
	private BigDecimal productPrice;

	@Column(nullable = false)
	private int quantity;

	@Builder(access = PRIVATE)
	private OrderItem(Order order, Product product, String productName, BigDecimal productPrice, int quantity) {
		this.order = order;
		this.product = product;
		this.productName = productName;
		this.productPrice = productPrice;
		this.quantity = quantity;
	}

	static OrderItem of(Order order, Product product, int quantity) {
		return OrderItem.builder()
				.order(order)
				.product(product)
				.productName(product.getTitle())
				.productPrice(product.getPrice())
				.quantity(quantity)
				.build();
	}

	public BigDecimal getLineAmount() {
		return this.productPrice.multiply(BigDecimal.valueOf(this.quantity));
	}
}
