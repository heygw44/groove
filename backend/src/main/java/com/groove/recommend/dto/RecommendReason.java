package com.groove.recommend.dto;

/** 추천 이유. 선언 순서가 동점일 때 표기 우선순위다. */
public enum RecommendReason {
	TASTE_GENRE,
	TASTE_ARTIST,
	TASTE_DECADE,
	SAME_ARTIST,
	SAME_GENRE,
	SAME_LABEL,
	SAME_DECADE,
	BOUGHT_TOGETHER,
	RECENTLY_VIEWED_SIMILAR
}
