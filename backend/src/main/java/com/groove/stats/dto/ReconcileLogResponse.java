package com.groove.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.groove.stats.entity.SalesReconcileLog;

public record ReconcileLogResponse(
		Long id,
		LocalDate saleDate,
		String metric,
		String severity,
		BigDecimal expectedValue,
		BigDecimal actualValue,
		boolean repaired,
		LocalDateTime createdAt
) {

	public static ReconcileLogResponse from(SalesReconcileLog reconcileLog) {
		return new ReconcileLogResponse(
				reconcileLog.getId(),
				reconcileLog.getSaleDate(),
				reconcileLog.getMetric().name(),
				reconcileLog.getSeverity().name(),
				reconcileLog.getExpectedValue(),
				reconcileLog.getActualValue(),
				reconcileLog.isRepaired(),
				reconcileLog.getCreatedAt());
	}
}
