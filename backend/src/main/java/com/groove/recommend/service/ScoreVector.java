package com.groove.recommend.service;

import java.util.EnumMap;
import java.util.Set;

import com.groove.recommend.dto.RecommendReason;

/**
 * 후보 하나에 대한 매칭 벡터. 가중치와 무관하다 — 이유별 매칭 강도(현재는 0/1)와 최근 조회로만
 * 성립한 매칭 여부, 공동구매 원점수만 담는다. 가중치를 곱해 점수로 바꾸는 건 {@link RecommendScorer#score}
 * 의 몫이다.
 */
public record ScoreVector(EnumMap<RecommendReason, Double> matchStrength, Set<RecommendReason> recentOnlyReasons,
		double coPurchaseScore) {
}
