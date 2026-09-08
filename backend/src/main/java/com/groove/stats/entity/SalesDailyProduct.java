package com.groove.stats.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.groove.global.common.BaseTimeEntity;
import com.groove.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜 x 상품별 판매 사전 집계. {@link SalesDaily} 와 달리 판매가 없는 (날짜, 상품) 조합은 행을 만들지 않는다
 * — 상품 수 x 365일로 두면 행이 폭발한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "sales_daily_product")
public class SalesDailyProduct extends BaseTimeEntity {

	@EmbeddedId
	private SalesDailyProductId id;

	@MapsId("productId")
	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_sales_daily_product_product"))
	private Product product;

	@Column(nullable = false)
	private long soldQuantity;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal salesAmount;

	@Column(nullable = false)
	private long orderCount;

	@Column(name = "aggregated_at", nullable = false)
	private LocalDateTime aggregatedAt;

	private SalesDailyProduct(SalesDailyProductId id, Product product, long soldQuantity, BigDecimal salesAmount,
			long orderCount, LocalDateTime aggregatedAt) {
		this.id = id;
		this.product = product;
		this.soldQuantity = soldQuantity;
		this.salesAmount = salesAmount;
		this.orderCount = orderCount;
		this.aggregatedAt = aggregatedAt;
	}

	public static SalesDailyProduct of(LocalDate saleDate, Product product, long soldQuantity,
			BigDecimal salesAmount, long orderCount, LocalDateTime aggregatedAt) {
		SalesDailyProductId id = SalesDailyProductId.of(saleDate, product.getId());
		return new SalesDailyProduct(id, product, soldQuantity, salesAmount, orderCount, aggregatedAt);
	}

	/** 재집계 결과로 값을 덮어쓴다. 가산이 아니라 항상 원본 재계산 값으로 교체해야 재실행이 멱등하다. */
	public void replace(long soldQuantity, BigDecimal salesAmount, long orderCount, LocalDateTime aggregatedAt) {
		this.soldQuantity = soldQuantity;
		this.salesAmount = salesAmount;
		this.orderCount = orderCount;
		this.aggregatedAt = aggregatedAt;
	}

	public LocalDate getSaleDate() {
		return id.getSaleDate();
	}
}
