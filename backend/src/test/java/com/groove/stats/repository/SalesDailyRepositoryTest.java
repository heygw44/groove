package com.groove.stats.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.stats.entity.SalesDaily;
import com.groove.support.DataJpaTestSupport;

/** 공유 테스트 DB 오염을 피하기 위해 이 테스트만 쓰는 먼 날짜를 쓴다. */
class SalesDailyRepositoryTest extends DataJpaTestSupport {

	private static final LocalDate SAVE_DATE = LocalDate.of(2031, 8, 1);

	@Autowired
	private SalesDailyRepository salesDailyRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 sale_date 로 저장하면 갱신되고 행이 하나만 남는다")
		void upsertsBySaleDateAsPrimaryKey() {
			// given
			LocalDateTime firstAggregatedAt = LocalDateTime.of(2031, 8, 1, 1, 0, 0);
			SalesDaily first = SalesDaily.of(SAVE_DATE, 1, new BigDecimal("10000"), 0, BigDecimal.ZERO,
					firstAggregatedAt);
			salesDailyRepository.save(first);
			salesDailyRepository.flush();

			// when
			LocalDateTime secondAggregatedAt = LocalDateTime.of(2031, 8, 1, 2, 0, 0);
			SalesDaily second = SalesDaily.of(SAVE_DATE, 2, new BigDecimal("20000"), 1, new BigDecimal("5000"),
					secondAggregatedAt);
			salesDailyRepository.save(second);
			salesDailyRepository.flush();

			// then
			List<SalesDaily> found = salesDailyRepository.findAllBySaleDateBetweenOrderBySaleDate(SAVE_DATE,
					SAVE_DATE);
			assertThat(found).hasSize(1);
			SalesDaily saved = found.get(0);
			assertThat(saved.getOrderCount()).isEqualTo(2);
			assertThat(saved.getSalesAmount()).isEqualByComparingTo(new BigDecimal("20000"));
			assertThat(saved.getAggregatedAt().truncatedTo(ChronoUnit.SECONDS))
					.isEqualTo(secondAggregatedAt.truncatedTo(ChronoUnit.SECONDS));
		}
	}

	@Nested
	@DisplayName("findAllBySaleDateBetweenOrderBySaleDate()")
	class FindAllBySaleDateBetweenOrderBySaleDate {

		@Test
		@DisplayName("기간 안의 행만 날짜 오름차순으로 반환한다")
		void returnsRowsInRangeOrderedByDate() {
			// given
			LocalDate from = LocalDate.of(2031, 9, 1);
			LocalDate middle = LocalDate.of(2031, 9, 2);
			LocalDate to = LocalDate.of(2031, 9, 3);
			LocalDate outOfRange = LocalDate.of(2031, 9, 10);
			LocalDateTime aggregatedAt = LocalDateTime.of(2031, 9, 1, 0, 0);

			salesDailyRepository.save(SalesDaily.of(to, 1, BigDecimal.TEN, 0, BigDecimal.ZERO, aggregatedAt));
			salesDailyRepository.save(SalesDaily.of(from, 1, BigDecimal.TEN, 0, BigDecimal.ZERO, aggregatedAt));
			salesDailyRepository.save(SalesDaily.of(middle, 1, BigDecimal.TEN, 0, BigDecimal.ZERO, aggregatedAt));
			salesDailyRepository.save(SalesDaily.of(outOfRange, 1, BigDecimal.TEN, 0, BigDecimal.ZERO, aggregatedAt));
			salesDailyRepository.flush();

			// when
			List<SalesDaily> result = salesDailyRepository.findAllBySaleDateBetweenOrderBySaleDate(from, to);

			// then
			assertThat(result).extracting(SalesDaily::getSaleDate).containsExactly(from, middle, to);
		}
	}
}
