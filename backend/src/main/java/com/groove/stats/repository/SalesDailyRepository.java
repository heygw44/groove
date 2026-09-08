package com.groove.stats.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.stats.entity.SalesDaily;

public interface SalesDailyRepository extends JpaRepository<SalesDaily, LocalDate> {

	List<SalesDaily> findAllBySaleDateBetweenOrderBySaleDate(LocalDate from, LocalDate to);

	// 원본 재집계 결과는 가산이 아니라 항상 VALUES(...) 로 덮어쓴다. 취소로 값이 줄어드는 경우가 이걸로 해결된다.
	// 매퍼가 결제 0건인 날도 0 값 1행을 돌려주므로 이 UPSERT 는 매번 호출된다 — 별도의 0 리셋이 필요 없다.
	@Modifying
	@Query(value = """
			INSERT INTO sales_daily
				(sale_date, order_count, sales_amount, cancel_count, cancel_amount,
				aggregated_at, created_at, updated_at)
			VALUES (:saleDate, :orderCount, :salesAmount, :cancelCount, :cancelAmount, :aggregatedAt, :aggregatedAt,
				:aggregatedAt)
			ON DUPLICATE KEY UPDATE
				order_count = VALUES(order_count),
				sales_amount = VALUES(sales_amount),
				cancel_count = VALUES(cancel_count),
				cancel_amount = VALUES(cancel_amount),
				aggregated_at = VALUES(aggregated_at),
				updated_at = VALUES(updated_at)
			""", nativeQuery = true)
	void upsert(@Param("saleDate") LocalDate saleDate, @Param("orderCount") long orderCount,
			@Param("salesAmount") BigDecimal salesAmount, @Param("cancelCount") long cancelCount,
			@Param("cancelAmount") BigDecimal cancelAmount, @Param("aggregatedAt") LocalDateTime aggregatedAt);
}
