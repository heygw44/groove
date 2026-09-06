package com.groove.recommend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.recommend.service.RecentViewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "RecentView", description = "최근 본 상품")
@RestController
@RequestMapping("/api/v1/members/me/recent-views")
@RequiredArgsConstructor
public class RecentViewController {

	private final RecentViewService recentViewService;

	@Operation(summary = "최근 본 상품 조회", description = "최근 조회순 최대 20개. HIDDEN 상품은 제외된다.")
	@GetMapping
	public ApiResponse<List<ProductSummaryResponse>> getRecentViews(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(recentViewService.getRecentViews(loginMember.id()));
	}
}
