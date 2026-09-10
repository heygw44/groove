package com.groove.recommend.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.groove.recommend.dto.RecommendReason;

import lombok.RequiredArgsConstructor;

/** 규칙 기반 추천 점수 계산기. 상태를 갖지 않는다(가중치는 주입받는다). */
@Component
@RequiredArgsConstructor
public class RecommendScorer {

	public static final int MAX_REASONS = 2;

	private final RecommendWeights weights;

	/** 취향 프로필만으로 점수를 매길 때 쓴다(시드·공동구매 없음). */
	public ScoreResult scoreTaste(ProductFeature candidate, TasteSignal taste) {
		return score(candidate, taste, List.of(), Set.of(), 0);
	}

	/** 콘텐츠 점수(취향+시드 유사도) + 공동구매 점수를 합산한다. */
	public ScoreResult score(ProductFeature candidate, TasteSignal taste, Collection<ProductFeature> seeds,
			Set<Long> recentOnlySeedIds, double coPurchaseCount) {
		ScoreVector vector = vectorize(candidate, taste, seeds, recentOnlySeedIds, coPurchaseCount);
		return score(vector, weights);
	}

	/**
	 * 후보 하나의 매칭 벡터를 만든다. 가중치와 완전히 무관하다 — (회원 x 후보) 벡터를 한 번 만들어 두면
	 * 가중치 조합을 바꿔가며 재평가할 때 이 계산을 다시 할 필요가 없다.
	 */
	public ScoreVector vectorize(ProductFeature candidate, TasteSignal taste, Collection<ProductFeature> seeds,
			Set<Long> recentOnlySeedIds, double coPurchaseScore) {
		EnumMap<RecommendReason, Double> matchStrength = new EnumMap<>(RecommendReason.class);
		Set<RecommendReason> recentOnlyReasons = EnumSet.noneOf(RecommendReason.class);

		addTasteMatch(matchStrength, RecommendReason.TASTE_ARTIST,
				candidate.artistId() != null && taste.artistIds().contains(candidate.artistId()));
		addTasteMatch(matchStrength, RecommendReason.TASTE_GENRE,
				!Collections.disjoint(candidate.genreIds(), taste.genreIds()));
		addTasteMatch(matchStrength, RecommendReason.TASTE_DECADE,
				candidate.decade() != null && taste.decades().contains(candidate.decade()));

		addSameDimensionMatch(matchStrength, recentOnlyReasons, RecommendReason.SAME_ARTIST,
				matchingSeedsByArtist(candidate, seeds), recentOnlySeedIds);
		addSameDimensionMatch(matchStrength, recentOnlyReasons, RecommendReason.SAME_GENRE,
				matchingSeedsByGenre(candidate, seeds), recentOnlySeedIds);
		addSameDimensionMatch(matchStrength, recentOnlyReasons, RecommendReason.SAME_LABEL,
				matchingSeedsByLabel(candidate, seeds), recentOnlySeedIds);
		addSameDimensionMatch(matchStrength, recentOnlyReasons, RecommendReason.SAME_DECADE,
				matchingSeedsByDecade(candidate, seeds), recentOnlySeedIds);

		return new ScoreVector(matchStrength, recentOnlyReasons, coPurchaseScore);
	}

	/** 매칭 벡터에 가중치를 적용해 점수를 매긴다. */
	public ScoreResult score(ScoreVector vector, RecommendWeights scoreWeights) {
		Map<RecommendReason, Double> contributions = new EnumMap<>(RecommendReason.class);
		double contentScore = 0;

		for (Map.Entry<RecommendReason, Double> entry : vector.matchStrength().entrySet()) {
			RecommendReason reason = entry.getKey();
			double contribution = scoreWeights.weightOf(reason) * entry.getValue();
			contentScore += contribution;

			RecommendReason effectiveReason = vector.recentOnlyReasons().contains(reason)
					? RecommendReason.RECENTLY_VIEWED_SIMILAR
					: reason;
			contributions.merge(effectiveReason, contribution, Double::sum);
		}

		double coPurchaseContribution = vector.coPurchaseScore() * scoreWeights.coPurchase();
		if (vector.coPurchaseScore() > 0) {
			contributions.merge(RecommendReason.BOUGHT_TOGETHER, coPurchaseContribution, Double::sum);
		}
		double totalScore = contentScore + coPurchaseContribution;

		return new ScoreResult(contentScore, totalScore, contributions);
	}

	/** 주입된 기본 가중치로 매칭 벡터에 점수를 매긴다. */
	public ScoreResult score(ScoreVector vector) {
		return score(vector, weights);
	}

	public boolean matchesTaste(ScoreResult tasteResult) {
		return tasteResult.contentScore() >= weights.tasteMatchThreshold() - 1e-9;
	}

	private void addTasteMatch(Map<RecommendReason, Double> matchStrength, RecommendReason reason, boolean matched) {
		if (matched) {
			matchStrength.put(reason, 1.0);
		}
	}

	/** 매칭 시드가 전부 recentOnlySeedIds 소속이면 recentOnlyReasons 에 표시한다. 점수가 아니라 라벨링에만 쓰인다. */
	private void addSameDimensionMatch(Map<RecommendReason, Double> matchStrength,
			Set<RecommendReason> recentOnlyReasons, RecommendReason reason, List<ProductFeature> matchingSeeds,
			Set<Long> recentOnlySeedIds) {
		if (matchingSeeds.isEmpty()) {
			return;
		}
		matchStrength.put(reason, 1.0);
		boolean allRecentOnly = matchingSeeds.stream().allMatch(seed -> recentOnlySeedIds.contains(seed.id()));
		if (allRecentOnly) {
			recentOnlyReasons.add(reason);
		}
	}

	private List<ProductFeature> matchingSeedsByArtist(ProductFeature candidate, Collection<ProductFeature> seeds) {
		if (candidate.artistId() == null) {
			return List.of();
		}
		return seeds.stream()
				.filter(seed -> candidate.artistId().equals(seed.artistId()))
				.toList();
	}

	private List<ProductFeature> matchingSeedsByGenre(ProductFeature candidate, Collection<ProductFeature> seeds) {
		return seeds.stream()
				.filter(seed -> !Collections.disjoint(candidate.genreIds(), seed.genreIds()))
				.toList();
	}

	private List<ProductFeature> matchingSeedsByLabel(ProductFeature candidate, Collection<ProductFeature> seeds) {
		if (candidate.labelId() == null) {
			return List.of();
		}
		return seeds.stream()
				.filter(seed -> candidate.labelId().equals(seed.labelId()))
				.toList();
	}

	private List<ProductFeature> matchingSeedsByDecade(ProductFeature candidate, Collection<ProductFeature> seeds) {
		if (candidate.decade() == null) {
			return List.of();
		}
		return seeds.stream()
				.filter(seed -> candidate.decade().equals(seed.decade()))
				.toList();
	}

	/** 콘텐츠 점수·최종 점수·이유별 기여도. */
	public record ScoreResult(double contentScore, double totalScore, Map<RecommendReason, Double> contributions) {

		public List<RecommendReason> topReasons() {
			return contributions.entrySet().stream()
					.filter(entry -> entry.getValue() > 0)
					.sorted(Comparator.<Map.Entry<RecommendReason, Double>>comparingDouble(Map.Entry::getValue)
							.reversed()
							.thenComparing(entry -> entry.getKey().ordinal()))
					.map(Map.Entry::getKey)
					.limit(MAX_REASONS)
					.toList();
		}
	}
}
