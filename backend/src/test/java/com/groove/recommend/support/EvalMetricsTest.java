package com.groove.recommend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvalMetricsTest {

	@Nested
	@DisplayName("MeasurementStats.of()")
	class MeasurementStatsOf {

		@Test
		@DisplayName("여러 값을 집계하면 표본 표준편차로 표준오차와 평균 하한을 계산한다")
		void computesStandardErrorFromSampleStdDev() {
			// given
			List<Double> values = List.of(0.1, 0.2, 0.3, 0.4);
			double expectedStdDev = Math.sqrt(0.05 / 3);

			// when
			EvalMetrics.MeasurementStats stats = EvalMetrics.MeasurementStats.of(values);

			// then
			assertThat(stats.mean()).isCloseTo(0.25, within(1e-9));
			assertThat(stats.stdDev()).isCloseTo(expectedStdDev, within(1e-9));
			assertThat(stats.standardError()).isCloseTo(expectedStdDev / 2, within(1e-9));
			assertThat(stats.meanLowerBound()).isCloseTo(0.25 - expectedStdDev, within(1e-9));
		}

		@Test
		@DisplayName("값이 하나면 표준편차와 표준오차는 0이고 평균 하한은 평균과 같다")
		void returnsMeanAsLowerBoundForSingleValue() {
			// given
			List<Double> values = List.of(0.25);

			// when
			EvalMetrics.MeasurementStats stats = EvalMetrics.MeasurementStats.of(values);

			// then
			assertThat(stats.stdDev()).isZero();
			assertThat(stats.standardError()).isZero();
			assertThat(stats.meanLowerBound()).isCloseTo(stats.mean(), within(1e-9));
		}

		@Test
		@DisplayName("여러 값을 집계해도 폴드 단위 하한은 평균에서 표준편차의 두 배를 뺀다")
		void keepsFoldLowerBoundBasedOnSampleStdDev() {
			// given
			List<Double> values = List.of(0.1, 0.2, 0.3, 0.4);

			// when
			EvalMetrics.MeasurementStats stats = EvalMetrics.MeasurementStats.of(values);

			// then
			assertThat(stats.lowerBound()).isCloseTo(stats.mean() - 2 * stats.stdDev(), within(1e-9));
		}
	}
}
