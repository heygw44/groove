package com.groove.recommend.dto;

import java.util.List;

public record TasteMatchResponse(boolean matched, List<RecommendReason> reasons) {

	public static TasteMatchResponse none() {
		return new TasteMatchResponse(false, List.of());
	}
}
