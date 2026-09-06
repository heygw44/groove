package com.groove.recommend.dto;

import java.util.List;

public record HomeRecommendResponse(boolean profileRequired, List<RecommendItemResponse> items) {

	public static HomeRecommendResponse requiresProfile() {
		return new HomeRecommendResponse(true, List.of());
	}

	/** 취향·행동 신호가 전혀 없는 회원에게 인기 상품으로 대신 채운 홈 추천. 온보딩 유도는 그대로 유지한다. */
	public static HomeRecommendResponse requiresProfileWithPopularFallback(List<RecommendItemResponse> items) {
		return new HomeRecommendResponse(true, items);
	}

	public static HomeRecommendResponse of(List<RecommendItemResponse> items) {
		return new HomeRecommendResponse(false, items);
	}
}
