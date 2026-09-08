package com.groove.admin.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsRow;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductStatsCondition;

/** 관리자 대시보드 집계 전용. JPA 로 표현하기 번거로운 그룹핑/파생 컬럼 쿼리를 담당한다. */
@Mapper
public interface AdminStatsMapper {

	List<DailySalesResponse> findDailySales(@Param("from") LocalDate from, @Param("to") LocalDate to);

	List<PopularProductResponse> findPopularProducts(PopularProductStatsCondition condition);

	/** 기간 내 가장 오래된 집계 시각. {@code sales_daily} 에 기간 내 행이 하나도 없으면 null 이다. */
	LocalDateTime findAggregatedAt(@Param("from") LocalDate from, @Param("to") LocalDate to);

	List<LimitedDropStatsRow> findLimitedDropStats();

	AdminStatsSummaryResponse findSummary(@Param("todayStart") LocalDateTime todayStart,
			@Param("tomorrowStart") LocalDateTime tomorrowStart);
}
