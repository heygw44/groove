package com.groove.inventory.entity;

import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 재고 입출고 이력. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "stock_history",
		indexes = @Index(name = "idx_stock_history_stock", columnList = "stock_id, created_at"))
public class StockHistory extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = LAZY)
	@JoinColumn(name = "stock_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_history_stock"))
	private Stock stock;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "change_type", nullable = false, length = 20)
	private StockChangeType changeType;

	@Column(name = "quantity_delta", nullable = false)
	private int quantityDelta;

	@Column(length = 200)
	private String reason;

	@Builder(access = PRIVATE)
	private StockHistory(Stock stock, StockChangeType changeType, int quantityDelta, String reason) {
		this.stock = stock;
		this.changeType = changeType;
		this.quantityDelta = quantityDelta;
		this.reason = reason;
	}

	public static StockHistory of(Stock stock, StockChangeType changeType, int quantityDelta, String reason) {
		return StockHistory.builder()
				.stock(stock)
				.changeType(changeType)
				.quantityDelta(quantityDelta)
				.reason(reason)
				.build();
	}
}
