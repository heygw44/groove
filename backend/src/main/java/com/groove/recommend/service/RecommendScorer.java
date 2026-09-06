package com.groove.recommend.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.groove.recommend.dto.RecommendReason;

/** 규칙 기반 추천 점수 계산기. 상태를 갖지 않는다. */
@Component
public class RecommendScorer {

	public static final int TASTE_MATCH_THRESHOLD = 3;
	public static final int MAX_REASONS = 2;
	static final int CO_PURCHASE_MULTIPLIER = 2;

	private static final Map<RecommendReason, Integer> WEIGHTS = new EnumMap<>(RecommendReason.class);

	static {
		WEIGHTS.put(RecommendReason.TASTE_ARTIST, 5);
		WEIGHTS.put(RecommendReason.SAME_ARTIST, 4);
		WEIGHTS.put(RecommendReason.TASTE_GENRE, 3);
		WEIGHTS.put(RecommendReason.SAME_GENRE, 2);
		WEIGHTS.put(RecommendReason.SAME_LABEL, 2);
		WEIGHTS.put(RecommendReason.TASTE_DECADE, 1);
		WEIGHTS.put(RecommendReason.SAME_DECADE, 1);
	}

	/** 취향 프로필만으로 점수를 매길 때 쓴다(시드·공동구매 없음). */
	public ScoreResult scoreTaste(ProductFeature candidate, TasteSignal taste) {
		return score(candidate, taste, List.of(), Set.of(), 0);
	}

	/** 콘텐츠 점수(취향+시드 유사도) + 공동구매 점수를 합산한다. */
	public ScoreResult score(ProductFeature candidate, TasteSignal taste, Collection<ProductFeature> seeds,
			Set<Long> recentOnlySeedIds, double coPurchaseCount) {
		Map<RecommendReason, Double> contributions = new EnumMap<>(RecommendReason.class);

		addTasteContribution(contributions, RecommendReason.TASTE_ARTIST,
				candidate.artistId() != null && taste.artistIds().contains(candidate.artistId()));
		addTasteContribution(contributions, RecommendReason.TASTE_GENRE,
				!Collections.disjoint(candidate.genreIds(), taste.genreIds()));
		addTasteContribution(contributions, RecommendReason.TASTE_DECADE,
				candidate.decade() != null && taste.decades().contains(candidate.decade()));

		addSameDimensionContribution(contributions, RecommendReason.SAME_ARTIST,
				matchingSeedsByArtist(candidate, seeds), recentOnlySeedIds);
		addSameDimensionContribution(contributions, RecommendReason.SAME_GENRE,
				matchingSeedsByGenre(candidate, seeds), recentOnlySeedIds);
		addSameDimensionContribution(contributions, RecommendReason.SAME_LABEL,
				matchingSeedsByLabel(candidate, seeds), recentOnlySeedIds);
		addSameDimensionContribution(contributions, RecommendReason.SAME_DECADE,
				matchingSeedsByDecade(candidate, seeds), recentOnlySeedIds);

		int contentScore = contributions.values().stream().mapToInt(Double::intValue).sum();

		if (coPurchaseCount > 0) {
			contributions.merge(RecommendReason.BOUGHT_TOGETHER, coPurchaseCount * CO_PURCHASE_MULTIPLIER,
					Double::sum);
		}
		double totalScore = contentScore + coPurchaseCount * CO_PURCHASE_MULTIPLIER;

		return new ScoreResult(contentScore, totalScore, contributions);
	}

	public boolean matchesTaste(ScoreResult tasteResult) {
		return tasteResult.contentScore() >= TASTE_MATCH_THRESHOLD;
	}

	private void addTasteContribution(Map<RecommendReason, Double> contributions, RecommendReason reason,
			boolean matched) {
		if (matched) {
			contributions.put(reason, (double) WEIGHTS.get(reason));
		}
	}

	/** 매칭 시드가 전부 recentOnlySeedIds 소속이면 RECENTLY_VIEWED_SIMILAR 로 치환해 가중치를 누적한다. */
	private void addSameDimensionContribution(Map<RecommendReason, Double> contributions, RecommendReason reason,
			List<ProductFeature> matchingSeeds, Set<Long> recentOnlySeedIds) {
		if (matchingSeeds.isEmpty()) {
			return;
		}
		boolean allRecentOnly = matchingSeeds.stream().allMatch(seed -> recentOnlySeedIds.contains(seed.id()));
		RecommendReason effectiveReason = allRecentOnly ? RecommendReason.RECENTLY_VIEWED_SIMILAR : reason;
		contributions.merge(effectiveReason, (double) WEIGHTS.get(reason), Double::sum);
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
	public record ScoreResult(int contentScore, double totalScore, Map<RecommendReason, Double> contributions) {

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
