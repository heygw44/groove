package com.groove.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductStatsRequest;
import com.groove.admin.dto.StatsPeriodRequest;
import com.groove.admin.service.AdminStatsService;
import com.groove.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin Stats", description = "관리자 대시보드 통계")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

	private final AdminStatsService adminStatsService;

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
}
