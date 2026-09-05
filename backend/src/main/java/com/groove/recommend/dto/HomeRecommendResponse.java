package com.groove.recommend.dto;

import java.util.List;

public record HomeRecommendResponse(boolean profileRequired, List<RecommendItemResponse> items) {

	public static HomeRecommendResponse requiresProfile() {
		return new HomeRecommendResponse(true, List.of());
	}

	public static HomeRecommendResponse of(List<RecommendItemResponse> items) {
		return new HomeRecommendResponse(false, items);
	}
}
