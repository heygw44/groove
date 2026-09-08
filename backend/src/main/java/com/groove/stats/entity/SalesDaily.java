package com.groove.stats.entity;

import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜별 매출 사전 집계. 판매가 없는 날도 0 행을 만든다 — "빈 구간"과 "아직 집계되지 않은 구간"을 구분해야 하기 때문이다.
 * PK 를 {@code sale_date} 자연키로 둔 이유: 조회가 늘 기간 range 라 클러스터드 인덱스 range 스캔으로 정렬까지 해소된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "sales_daily")
public class SalesDaily extends BaseTimeEntity {

	@Id
	@Column(name = "sale_date")
	private LocalDate saleDate;

	@Column(nullable = false)
	private long orderCount;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal salesAmount;

	@Column(nullable = false)
	private long cancelCount;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal cancelAmount;

	@Column(name = "aggregated_at", nullable = false)
	private LocalDateTime aggregatedAt;

	private SalesDaily(LocalDate saleDate, long orderCount, BigDecimal salesAmount, long cancelCount,
			BigDecimal cancelAmount, LocalDateTime aggregatedAt) {
		this.saleDate = saleDate;
		this.orderCount = orderCount;
		this.salesAmount = salesAmount;
		this.cancelCount = cancelCount;
		this.cancelAmount = cancelAmount;
		this.aggregatedAt = aggregatedAt;
	}

	public static SalesDaily of(LocalDate saleDate, long orderCount, BigDecimal salesAmount, long cancelCount,
			BigDecimal cancelAmount, LocalDateTime aggregatedAt) {
		return new SalesDaily(saleDate, orderCount, salesAmount, cancelCount, cancelAmount, aggregatedAt);
	}

	/** 재집계 결과로 값을 덮어쓴다. 가산이 아니라 항상 원본 재계산 값으로 교체해야 재실행이 멱등하다. */
	public void replace(long orderCount, BigDecimal salesAmount, long cancelCount, BigDecimal cancelAmount,
			LocalDateTime aggregatedAt) {
		this.orderCount = orderCount;
		this.salesAmount = salesAmount;
		this.cancelCount = cancelCount;
		this.cancelAmount = cancelAmount;
		this.aggregatedAt = aggregatedAt;
	}
}
