package com.groove.recommend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.recommend.dto.HomeRecommendResponse;
import com.groove.recommend.dto.RecommendItemResponse;
import com.groove.recommend.service.RecommendService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Recommend", description = "상품 추천")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendController {

	private final RecommendService recommendService;

	@Operation(summary = "홈 추천 상품 조회", description = "취향 프로필·위시·구매·최근 본 상품을 바탕으로 추천한다")
	@GetMapping("/recommend/home")
	public ApiResponse<HomeRecommendResponse> recommendHome(@AuthMember LoginMember loginMember,
			@RequestParam(required = false) Integer size) {
		return ApiResponse.ok(recommendService.recommendHome(loginMember.id(), size));
	}

	@Operation(summary = "관련 상품 조회", description = "로그인 시 취향·공동구매 신호를 함께 반영한다")
	@SecurityRequirements
	@GetMapping("/products/{id}/related")
	public ApiResponse<List<RecommendItemResponse>> recommendRelated(@PathVariable Long id,
			@RequestParam(required = false) Integer size, @AuthMember(required = false) LoginMember loginMember) {
		return ApiResponse.ok(recommendService.recommendRelated(id, memberIdOf(loginMember), size));
	}

	private static Long memberIdOf(LoginMember loginMember) {
		return loginMember == null ? null : loginMember.id();
	}
}
