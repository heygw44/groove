package com.groove.inventory.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

import com.groove.global.common.BaseTimeEntity;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품 재고. 동시 조정 충돌은 {@code version} 낙관적 락으로 방어한다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "stock",
		uniqueConstraints = @UniqueConstraint(name = "uk_stock_product", columnNames = "product_id"))
@Check(name = "chk_stock_quantity", constraints = "quantity >= 0")
public class Stock extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_product"))
	private Product product;

	@Column(nullable = false)
	@ColumnDefault("0")
	private int quantity;

	@Version
	@Column(nullable = false)
	@ColumnDefault("0")
	private Long version;

	@Builder(access = PRIVATE)
	private Stock(Product product, int quantity) {
		this.product = product;
		this.quantity = quantity;
	}

	public static Stock create(Product product, int initialQuantity) {
		if (initialQuantity < 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		Stock stock = Stock.builder()
				.product(product)
				.quantity(initialQuantity)
				.build();
		stock.syncProductStatus();
		return stock;
	}

	public void increase(int amount) {
		if (amount <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.quantity += amount;
		syncProductStatus();
	}

	public void decrease(int amount) {
		if (amount <= 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		if (this.quantity < amount) {
			throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
		}
		this.quantity -= amount;
		syncProductStatus();
	}

	public void replaceQuantity(int newQuantity) {
		if (newQuantity < 0) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		this.quantity = newQuantity;
		syncProductStatus();
	}

	private void syncProductStatus() {
		if (this.quantity == 0 && this.product.isOnSale()) {
			this.product.markSoldOut();
		} else if (this.quantity > 0 && this.product.getStatus() == ProductStatus.SOLD_OUT) {
			this.product.resume();
		}
	}
}
