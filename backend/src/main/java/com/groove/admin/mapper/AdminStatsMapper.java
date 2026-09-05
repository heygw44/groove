package com.groove.admin.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductStatsCondition;

/** 관리자 대시보드 집계 전용. JPA 로 표현하기 번거로운 그룹핑/파생 컬럼 쿼리를 담당한다. */
@Mapper
public interface AdminStatsMapper {

	List<DailySalesResponse> findDailySales(@Param("fromAt") LocalDateTime fromAt,
			@Param("toExclusiveAt") LocalDateTime toExclusiveAt);

	List<PopularProductResponse> findPopularProducts(PopularProductStatsCondition condition);

	List<LimitedDropStatsResponse> findLimitedDropStats();

	AdminStatsSummaryResponse findSummary(@Param("todayStart") LocalDateTime todayStart,
			@Param("tomorrowStart") LocalDateTime tomorrowStart);
}
