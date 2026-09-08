package com.groove.stats.entity;

import static lombok.AccessLevel.PROTECTED;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link SalesDailyProduct} 복합 키. */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class SalesDailyProductId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "sale_date")
	private LocalDate saleDate;

	@Column(name = "product_id")
	private Long productId;

	private SalesDailyProductId(LocalDate saleDate, Long productId) {
		this.saleDate = saleDate;
		this.productId = productId;
	}

	public static SalesDailyProductId of(LocalDate saleDate, Long productId) {
		return new SalesDailyProductId(saleDate, productId);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SalesDailyProductId that)) {
			return false;
		}
		return Objects.equals(saleDate, that.saleDate)
				&& Objects.equals(productId, that.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(saleDate, productId);
	}
}
