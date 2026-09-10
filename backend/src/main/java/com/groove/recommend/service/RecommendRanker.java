package com.groove.recommend.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 후보 채점·정렬·앨범 dedup·컷. 홈 추천·관련 상품·평가 하네스가 이 규칙을 공유한다. */
@Component
@RequiredArgsConstructor
public class RecommendRanker {

	private final RecommendScorer recommendScorer;
	private final RecommendWeights weights;

	/** 주입된 기본 가중치로 랭킹한다. */
	public List<RankedCandidate> rank(Map<Long, ProductFeature> features, TasteSignal taste,
			Collection<ProductFeature> seeds, Set<Long> recentOnlySeedIds, Map<Long, Double> coPurchaseScores,
			Set<Long> excludeIds, int size) {
		return rank(features, taste, seeds, recentOnlySeedIds, coPurchaseScores, excludeIds, size, weights);
	}

	/** ablation·스윕용 — 주입된 기본 가중치 대신 지정한 가중치로 랭킹한다. */
	public List<RankedCandidate> rank(Map<Long, ProductFeature> features, TasteSignal taste,
			Collection<ProductFeature> seeds, Set<Long> recentOnlySeedIds, Map<Long, Double> coPurchaseScores,
			Set<Long> excludeIds, int size, RecommendWeights rankingWeights) {
		// 베이지안 평점(bayes)은 스냅샷 전체(C)에 의존해 정적 상수로 못 두고 매 호출마다 만든다 — 상품 수백 건
		// 기준 O(n) 스캔이라 비용은 무시할 만하다.
		PopularityIndex popularityIndex = PopularityIndex.from(features.values());
		Comparator<RankedCandidate> rankingComparator = Comparator
				.comparingDouble((RankedCandidate candidate) -> candidate.score().totalScore())
				.reversed()
				.thenComparing(candidate -> popularityIndex.bayes(candidate.feature()), Comparator.reverseOrder())
				.thenComparing(candidate -> candidate.feature().createdAt(), Comparator.reverseOrder())
				.thenComparing(candidate -> candidate.feature().id(), Comparator.reverseOrder());

		List<RankedCandidate> sorted = features.values().stream()
				.filter(feature -> !feature.hidden())
				.filter(feature -> !excludeIds.contains(feature.id()))
				.map(feature -> {
					ScoreVector vector = recommendScorer.vectorize(feature, taste, seeds, recentOnlySeedIds,
							coPurchaseScores.getOrDefault(feature.id(), 0.0));
					return new RankedCandidate(feature, recommendScorer.score(vector, rankingWeights));
				})
				.filter(candidate -> candidate.score().totalScore() > 0)
				.sorted(rankingComparator)
				.toList();

		// 같은 앨범의 다른 프레싱은 나란히 상위를 차지하므로 앨범당 점수 1위만 남긴다. size 로 자르기 전에 걸러야 목록이 짧아지지 않는다.
		Set<Long> seenAlbumIds = new HashSet<>();
		List<RankedCandidate> picked = new ArrayList<>(size);
		for (RankedCandidate candidate : sorted) {
			if (!seenAlbumIds.add(candidate.feature().albumId())) {
				continue;
			}
			picked.add(candidate);
			if (picked.size() == size) {
				break;
			}
		}
		return picked;
	}

	public record RankedCandidate(ProductFeature feature, RecommendScorer.ScoreResult score) {
	}
}
