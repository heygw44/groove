package com.groove.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductStatsRequest;
import com.groove.admin.dto.StatsPeriodRequest;
import com.groove.admin.service.AdminStatsService;
import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.stats.dto.SalesAggregationRequest;
import com.groove.stats.dto.SalesAggregationResponse;
import com.groove.stats.service.SalesAggregationAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Stats", description = "관리자 대시보드 통계")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

	private final AdminStatsService adminStatsService;
	private final SalesAggregationAdminService salesAggregationAdminService;

	@Operation(summary = "일별 매출 통계")
	@GetMapping("/daily-sales")
	public ApiResponse<List<DailySalesResponse>> getDailySales(@ModelAttribute StatsPeriodRequest request) {
		return ApiResponse.ok(adminStatsService.getDailySales(request));
	}

	@Operation(summary = "인기 상품 통계")
	@GetMapping("/popular-products")
	public ApiResponse<List<PopularProductResponse>> getPopularProducts(
			@ModelAttribute PopularProductStatsRequest request) {
		return ApiResponse.ok(adminStatsService.getPopularProducts(request));
	}

	@Operation(summary = "한정반 드롭 현황 통계")
	@GetMapping("/limited-drops")
	public ApiResponse<List<LimitedDropStatsResponse>> getLimitedDropStats() {
		return ApiResponse.ok(adminStatsService.getLimitedDropStats());
	}

	@Operation(summary = "대시보드 요약 카드")
	@GetMapping("/summary")
	public ApiResponse<AdminStatsSummaryResponse> getSummary() {
		return ApiResponse.ok(adminStatsService.getSummary());
	}

	@Operation(summary = "사전 집계 수동 재집계", description = "운영 백필의 유일한 경로. 야간/증분 스케줄러와 같은 락을 공유한다")
	@PostMapping("/aggregations")
	public ApiResponse<SalesAggregationResponse> aggregate(@AuthMember LoginMember loginMember,
			@Valid @RequestBody SalesAggregationRequest request) {
		return ApiResponse.ok(salesAggregationAdminService.aggregate(loginMember.id(), request));
	}
}
