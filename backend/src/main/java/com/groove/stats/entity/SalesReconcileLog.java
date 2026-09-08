package com.groove.stats.entity;

import static lombok.AccessLevel.PROTECTED;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.groove.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사전 집계와 원본 재계산 값을 비교한 대사 이력. 대리키를 쓴다 — 조회가 range 가 아니라 최근순 나열이다. */
@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(name = "sales_reconcile_log",
		indexes = @Index(name = "idx_sales_reconcile_log_date", columnList = "sale_date, created_at"))
public class SalesReconcileLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sale_date", nullable = false)
	private LocalDate saleDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ReconcileMetric metric;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ReconcileSeverity severity;

	@Column(name = "expected_value", nullable = false, precision = 14, scale = 2)
	private BigDecimal expectedValue;

	@Column(name = "actual_value", nullable = false, precision = 14, scale = 2)
	private BigDecimal actualValue;

	@Column(nullable = false)
	private boolean repaired;

	private SalesReconcileLog(LocalDate saleDate, ReconcileMetric metric,
			ReconcileSeverity severity, BigDecimal expectedValue, BigDecimal actualValue) {
		this.saleDate = saleDate;
		this.metric = metric;
		this.severity = severity;
		this.expectedValue = expectedValue;
		this.actualValue = actualValue;
		this.repaired = false;
	}

	public static SalesReconcileLog of(LocalDate saleDate, ReconcileMetric metric,
			ReconcileSeverity severity, BigDecimal expectedValue, BigDecimal actualValue) {
		return new SalesReconcileLog(saleDate, metric, severity, expectedValue, actualValue);
	}

	public void repair() {
		this.repaired = true;
	}
}
