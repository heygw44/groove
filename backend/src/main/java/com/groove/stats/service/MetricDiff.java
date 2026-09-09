package com.groove.stats.service;

import java.math.BigDecimal;

import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;

/** 원본 재계산 값(expected)과 사전 집계 테이블 값(actual)이 어긋난 지표 한 건. */
record MetricDiff(ReconcileMetric metric, ReconcileSeverity severity, BigDecimal expectedValue,
		BigDecimal actualValue) {
}
