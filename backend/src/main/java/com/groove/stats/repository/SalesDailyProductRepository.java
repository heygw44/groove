package com.groove.stats.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.stats.entity.SalesDailyProduct;
import com.groove.stats.entity.SalesDailyProductId;

public interface SalesDailyProductRepository extends JpaRepository<SalesDailyProduct, SalesDailyProductId> {

	List<SalesDailyProduct> findAllByIdSaleDate(LocalDate saleDate);

	// 그날 팔렸다가 전부 취소돼 원본 집계에서 사라진 상품의 잔존 행을 지운다. 이번 실행 스탬프(runAt) 보다 오래된
	// aggregated_at 인 행만 지우므로, 삭제 대상은 실제로 사라진 행뿐이라 락 범위가 최소다.
	@Modifying
	@Query("DELETE FROM SalesDailyProduct s WHERE s.id.saleDate = :saleDate AND s.aggregatedAt < :runAt")
	int deleteStale(@Param("saleDate") LocalDate saleDate, @Param("runAt") LocalDateTime runAt);
}
