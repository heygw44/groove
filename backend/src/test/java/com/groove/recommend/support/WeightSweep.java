package com.groove.recommend.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.service.HomeSeeds;
import com.groove.recommend.service.PopularityIndex;
import com.groove.recommend.service.ProductFeature;
import com.groove.recommend.service.RecommendScorer;
import com.groove.recommend.service.RecommendWeights;
import com.groove.recommend.service.ScoreVector;
import com.groove.recommend.service.TasteSignal;

/**
 * 가중치 스윕(좌표상승)의 핵심 엔진. {@code ScoreVector} 가 가중치에 대해 선형이라는 성질을 이용해
 * (회원 × 후보) 벡터를 (kind, seed, fold) 당 한 번만 만들고, 가중치 구성을 바꿔가며 재평가할 때는
 * {@link RecommendScorer#score(ScoreVector, RecommendWeights)} 내적만 반복한다 — {@code RecommendRanker}
 * 를 직접 부르면 호출마다 vectorize 를 다시 하므로 이 클래스는 그 후처리(채점·정렬·앨범 dedup·컷)만
 * 별도로 재현한다({@link #rank}). 로직은 {@code RecommendRanker.rank()} 와 반드시 같아야 한다 — 단, 아티스트/레이블
 * 캡은 가중치와 무관한 별개 정책이라 여기서는 재현하지 않는다.
 */
public final class WeightSweep {

	private WeightSweep() {
	}

	/**
	 * 후보 하나의 매칭 벡터 + 동점 처리용 베이지안 평점. 취향 단독 매칭 벡터(RECENTLY/SAME_* 미포함)는 여기
	 * 저장하지 않는다 — 후보 전체(수백 건)에 저장하면 메모리를 배로 먹는데 실제로 쓰는 곳은 top-10 뿐이라
	 * {@link #matchesTaste} 가 필요할 때만 다시 계산한다.
	 */
	public record CandidateVector(ProductFeature feature, ScoreVector vector, double bayes) {
	}

	/** 회원 한 명, 폴드 하나의 벡터 스냅샷. 가중치와 무관해 여러 구성이 재사용한다. */
	public record MemberFoldVectors(Long memberId, TasteSignal taste, Set<Long> holdoutIds, boolean fallback,
			Set<Long> excludedForPopularity, List<CandidateVector> candidates) {
	}

	/** 좌표상승 한 번 돌린 결과. {@code visited} 는 탐색 중 평가한 모든 가중치(중간값 포함) — 널 테스트가 재사용한다. */
	public record AscentResult(RecommendWeights best, List<RecommendWeights> visited) {
	}

	private static final List<RecommendReason> CONTENT_DIMENSIONS = List.of(RecommendReason.TASTE_ARTIST,
			RecommendReason.SAME_ARTIST, RecommendReason.TASTE_GENRE, RecommendReason.SAME_GENRE,
			RecommendReason.SAME_LABEL, RecommendReason.TASTE_DECADE, RecommendReason.SAME_DECADE);
	private static final int MIN_DIMENSION_VALUE = 0;
	private static final int MAX_DIMENSION_VALUE = 8;
	private static final List<Double> CO_PURCHASE_CANDIDATES = List.of(0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 14.0, 20.0);
	private static final double TASTE_MATCH_EPSILON = 1e-9;

	// ==================== 벡터화 ====================

	/** members 전원의 (fold 하나) 벡터를 만든다. holdoutByMemberId 에 없는 회원은 홀드아웃이 비어 측정에서 빠진다. */
	public static List<MemberFoldVectors> vectorizeAll(RecommendScorer scorer, Map<Long, ProductFeature> features,
			PopularityIndex popularityIndex, List<EvalSignals> members, Map<Long, Set<Long>> holdoutByMemberId,
			CoPurchaseIndex coPurchaseIndex) {
		List<MemberFoldVectors> result = new ArrayList<>();
		for (EvalSignals signals : members) {
			Set<Long> holdout = holdoutByMemberId.getOrDefault(signals.memberId(), Set.of());
			result.add(vectorizeMember(scorer, features, popularityIndex, signals, holdout, coPurchaseIndex));
		}
		return result;
	}

	private static MemberFoldVectors vectorizeMember(RecommendScorer scorer, Map<Long, ProductFeature> features,
			PopularityIndex popularityIndex, EvalSignals signals, Set<Long> holdout, CoPurchaseIndex coPurchaseIndex) {
		EvalSignals foldSignals = signals.without(holdout);
		HomeSeeds homeSeeds = HomeSeeds.of(foldSignals.wishedIds(), foldSignals.purchasedIds(),
				foldSignals.recentIds());
		Set<Long> seedIds = homeSeeds.seedIds();

		Set<Long> excludedForPopularity = new HashSet<>(foldSignals.wishedIds());
		excludedForPopularity.addAll(foldSignals.purchasedIds());

		if (foldSignals.taste().isEmpty() && seedIds.isEmpty()) {
			return new MemberFoldVectors(signals.memberId(), foldSignals.taste(), holdout, true,
					excludedForPopularity, List.of());
		}

		List<ProductFeature> seeds = seedIds.stream().map(features::get).filter(Objects::nonNull).toList();
		Map<Long, Double> coPurchaseScores = aggregateCoPurchaseScores(coPurchaseIndex, seedIds);

		List<CandidateVector> candidates = features.values().stream()
				.filter(feature -> !feature.hidden())
				.filter(feature -> !seedIds.contains(feature.id()))
				.map(feature -> {
					ScoreVector vector = scorer.vectorize(feature, foldSignals.taste(), seeds,
							homeSeeds.recentOnlySeedIds(), coPurchaseScores.getOrDefault(feature.id(), 0.0));
					return new CandidateVector(feature, vector, popularityIndex.bayes(feature));
				})
				.toList();

		return new MemberFoldVectors(signals.memberId(), foldSignals.taste(), holdout, false, excludedForPopularity,
				candidates);
	}

	/** {@code RecommendService.aggregateCoPurchaseScores()} 와 동일 로직. */
	private static Map<Long, Double> aggregateCoPurchaseScores(CoPurchaseIndex coPurchaseIndex, Set<Long> seedIds) {
		if (seedIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Double> totalScores = new LinkedHashMap<>();
		for (Map<Long, Double> scoresByCandidate : coPurchaseIndex.findScores(seedIds).values()) {
			scoresByCandidate.forEach((candidateId, score) -> totalScores.merge(candidateId, score, Double::sum));
		}
		return totalScores;
	}

	// ==================== 랭킹(가중치 재평가) ====================

	/**
	 * 사전 계산한 벡터에 가중치를 적용해 {@code RecommendRanker.rank()} 와 같은 규칙(총점 필터 → 정렬 → 앨범
	 * dedup → 컷)으로 top-{@code size} 후보를 뽑는다. vectorize 를 다시 하지 않으므로 가중치 구성이 아무리
	 * 많아도 이 단계는 내적 + 정렬 비용만 든다. id 가 아니라 {@link CandidateVector} 를 그대로 돌려줘 호출부가
	 * (id → 후보) 역조회 맵을 따로 만들 필요가 없게 한다 — 후보가 회원당 수백 건이라 그 맵이 메모리를 크게 먹는다.
	 */
	public static List<CandidateVector> rank(RecommendScorer scorer, MemberFoldVectors vectors,
			RecommendWeights weights, int size) {
		if (vectors.fallback()) {
			return List.of();
		}

		List<ScoredCandidate> sorted = vectors.candidates().stream()
				.map(candidate -> new ScoredCandidate(candidate, scorer.score(candidate.vector(), weights)))
				.filter(scored -> scored.score().totalScore() > 0)
				.sorted(scoredCandidateComparator())
				.toList();

		Set<Long> seenAlbumIds = new HashSet<>();
		List<CandidateVector> picked = new ArrayList<>(size);
		for (ScoredCandidate scored : sorted) {
			if (!seenAlbumIds.add(scored.candidate().feature().albumId())) {
				continue;
			}
			picked.add(scored.candidate());
			if (picked.size() == size) {
				break;
			}
		}
		return picked;
	}

	private static Comparator<ScoredCandidate> scoredCandidateComparator() {
		return Comparator
				.comparingDouble((ScoredCandidate scored) -> scored.score().totalScore())
				.reversed()
				.thenComparing(scored -> scored.candidate().bayes(), Comparator.reverseOrder())
				.thenComparing(scored -> scored.candidate().feature().createdAt(), Comparator.reverseOrder())
				.thenComparing(scored -> scored.candidate().feature().id(), Comparator.reverseOrder());
	}

	/**
	 * {@code weights.tasteMatchThreshold()} 도 실험 대상 가중치 기준으로 판정한다(주입된 기본값 아님). 취향
	 * 단독 벡터는 저장해두지 않고 여기서 즉석 계산한다 — top-{@code size} 만 호출하므로 비용이 작다.
	 */
	public static boolean matchesTaste(ProductFeature feature, TasteSignal taste, RecommendScorer scorer,
			RecommendWeights weights) {
		ScoreVector tasteOnlyVector = scorer.vectorize(feature, taste, List.of(), Set.of(), 0);
		RecommendScorer.ScoreResult tasteScore = scorer.score(tasteOnlyVector, weights);
		return tasteScore.contentScore() >= weights.tasteMatchThreshold() - TASTE_MATCH_EPSILON;
	}

	// ==================== 좌표상승 ====================

	/**
	 * 차원 순서 고정(TASTE_ARTIST → SAME_ARTIST → TASTE_GENRE → SAME_GENRE → SAME_LABEL → TASTE_DECADE →
	 * SAME_DECADE) 좌표상승. 각 라운드마다 콘텐츠 차원 7개를 0~8 정수로, 공동구매를 별도 실수 후보로
	 * 순서대로 최적화한다. {@code visited} 에는 최종값이 된 것만이 아니라 탐색 중 평가한 모든 가중치가 담겨
	 * 널 테스트가 탐색 전체를 재현할 수 있게 한다.
	 */
	public static AscentResult ascend(Function<RecommendWeights, Double> objective, RecommendWeights start,
			int rounds) {
		List<RecommendWeights> visited = new ArrayList<>();
		RecommendWeights current = start;

		for (int round = 0; round < rounds; round++) {
			for (RecommendReason dimension : CONTENT_DIMENSIONS) {
				current = bestOfDimension(objective, current, dimension, visited);
			}
			current = bestOfCoPurchase(objective, current, visited);
		}
		return new AscentResult(current, visited);
	}

	private static RecommendWeights bestOfDimension(Function<RecommendWeights, Double> objective,
			RecommendWeights current, RecommendReason dimension, List<RecommendWeights> visited) {
		RecommendWeights best = current;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int value = MIN_DIMENSION_VALUE; value <= MAX_DIMENSION_VALUE; value++) {
			RecommendWeights trial = current.with(dimension, value);
			visited.add(trial);
			double score = objective.apply(trial);
			if (score > bestScore) {
				bestScore = score;
				best = trial;
			}
		}
		return best;
	}

	private static RecommendWeights bestOfCoPurchase(Function<RecommendWeights, Double> objective,
			RecommendWeights current, List<RecommendWeights> visited) {
		RecommendWeights best = current;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (double value : CO_PURCHASE_CANDIDATES) {
			RecommendWeights trial = current.withCoPurchase(value);
			visited.add(trial);
			double score = objective.apply(trial);
			if (score > bestScore) {
				bestScore = score;
				best = trial;
			}
		}
		return best;
	}

	private record ScoredCandidate(CandidateVector candidate, RecommendScorer.ScoreResult score) {
	}

	// ==================== 코호트 측정 파이프라인 ====================

	/**
	 * 코호트 하나(회원 목록 + 주문 바스켓)에 대해 WISH/PURCHASE 홀드아웃을 (seed, fold) 당 한 번만 벡터화해두고
	 * 가중치를 바꿔가며 재평가한다. {@code RecommendAblationTest} 가 구성마다 반복하던 홀드아웃 분할·공동구매
	 * 재집계·벡터화를 이 클래스가 한 번만 하고 여러 가중치 구성이 공유한다.
	 */
	public static final class CohortEval {

		private final KindEval wishEval;
		private final KindEval purchaseEval;

		private CohortEval(KindEval wishEval, KindEval purchaseEval) {
			this.wishEval = wishEval;
			this.purchaseEval = purchaseEval;
		}

		public static CohortEval build(RecommendScorer scorer, Map<Long, ProductFeature> features,
				PopularityIndex popularityIndex, List<EvalSignals> members, List<CoPurchaseBasket> baskets,
				List<Long> byPopularityIds, long candidateCount, long albumCandidateCount, int wishFoldCount,
				int purchaseFoldCount, List<Long> randomSeeds) {
			KindEval wishEval = KindEval.build(scorer, features, popularityIndex, members, baskets, byPopularityIds,
					candidateCount, albumCandidateCount, HoldoutKind.WISH, wishFoldCount, randomSeeds);
			KindEval purchaseEval = KindEval.build(scorer, features, popularityIndex, members, baskets,
					byPopularityIds, candidateCount, albumCandidateCount, HoldoutKind.PURCHASE, purchaseFoldCount,
					randomSeeds);
			return new CohortEval(wishEval, purchaseEval);
		}

		public EvalMetrics.FoldedRun wishRun(RecommendWeights weights) {
			return wishEval.run(weights);
		}

		public EvalMetrics.FoldedRun purchaseRun(RecommendWeights weights) {
			return purchaseEval.run(weights);
		}

		/** 좌표상승 목적함수. 위시·구매 recall@10 평균(원값, 기준 대비 Δ 아님)을 극대화한다. */
		public double combinedRecall(RecommendWeights weights) {
			return (wishRun(weights).recallStats().mean() + purchaseRun(weights).recallStats().mean()) / 2.0;
		}
	}

	/** WISH 또는 PURCHASE 홀드아웃 하나에 대한 (seed, fold) 벡터 스냅샷과 재평가. */
	private static final class KindEval {

		private static final int TOP_K = 10;

		private final HoldoutKind kind;
		private final int foldCount;
		private final List<Long> randomSeeds;
		private final List<SeedFold> seedFolds;
		private final RecommendScorer scorer;
		private final List<Long> byPopularityIds;
		private final long candidateCount;
		private final long albumCandidateCount;

		private KindEval(HoldoutKind kind, int foldCount, List<Long> randomSeeds, List<SeedFold> seedFolds,
				RecommendScorer scorer, List<Long> byPopularityIds, long candidateCount, long albumCandidateCount) {
			this.kind = kind;
			this.foldCount = foldCount;
			this.randomSeeds = randomSeeds;
			this.seedFolds = seedFolds;
			this.scorer = scorer;
			this.byPopularityIds = byPopularityIds;
			this.candidateCount = candidateCount;
			this.albumCandidateCount = albumCandidateCount;
		}

		/**
		 * 공동구매 인덱스와 회원 벡터를 (seed, fold) 당 한 번만 만든다. 이 조합을 회원 단위나 가중치 구성
		 * 단위로 쪼개 만들면 다른 폴드의 홀드아웃이 섞여 다시 누수가 된다.
		 */
		static KindEval build(RecommendScorer scorer, Map<Long, ProductFeature> features,
				PopularityIndex popularityIndex, List<EvalSignals> members, List<CoPurchaseBasket> baskets,
				List<Long> byPopularityIds, long candidateCount, long albumCandidateCount, HoldoutKind kind,
				int foldCount, List<Long> randomSeeds) {
			HoldoutSplitter splitter = new HoldoutSplitter();
			List<SeedFold> seedFolds = new ArrayList<>();
			for (Long seed : randomSeeds) {
				HoldoutSpec spec = new HoldoutSpec(kind, foldCount, seed);
				Map<Long, List<Set<Long>>> foldsByMemberId = members.stream()
						.collect(Collectors.toMap(EvalSignals::memberId, signals -> splitter.foldsOf(signals, spec)));
				for (int foldIndex = 0; foldIndex < foldCount; foldIndex++) {
					Map<Long, Set<Long>> holdoutByMemberId = new LinkedHashMap<>();
					int currentFoldIndex = foldIndex;
					foldsByMemberId.forEach((memberId, folds) -> holdoutByMemberId.put(memberId,
							folds.get(currentFoldIndex)));
					CoPurchaseIndex coPurchaseIndex = new InMemoryCoPurchaseIndex(baskets, holdoutByMemberId);
					List<MemberFoldVectors> memberVectors = WeightSweep.vectorizeAll(scorer, features,
							popularityIndex, members, holdoutByMemberId, coPurchaseIndex);
					seedFolds.add(new SeedFold(seed, foldIndex, memberVectors));
				}
			}
			return new KindEval(kind, foldCount, randomSeeds, seedFolds, scorer, byPopularityIds, candidateCount,
					albumCandidateCount);
		}

		EvalMetrics.FoldedRun run(RecommendWeights weights) {
			List<EvalMetrics.Measurement> measurements = new ArrayList<>();
			for (SeedFold seedFold : seedFolds) {
				List<EvalMetrics.MemberEvalResult> results = seedFold.members().stream()
						.map(memberVectors -> evaluateMember(memberVectors, weights))
						.toList();
				EvalMetrics.Summary summary = EvalMetrics.summarize(results, candidateCount, albumCandidateCount);
				measurements.add(new EvalMetrics.Measurement(seedFold.randomSeed(), seedFold.foldIndex(), summary));
			}
			return new EvalMetrics.FoldedRun(kind, foldCount, randomSeeds, measurements);
		}

		private EvalMetrics.MemberEvalResult evaluateMember(MemberFoldVectors vectors, RecommendWeights weights) {
			if (vectors.holdoutIds().isEmpty()) {
				return EvalMetrics.MemberEvalResult.empty(vectors.memberId());
			}
			if (vectors.fallback()) {
				return new EvalMetrics.MemberEvalResult(vectors.memberId(), vectors.holdoutIds(), List.of(), true,
						popularityHitCount(vectors));
			}
			List<CandidateVector> ranked = WeightSweep.rank(scorer, vectors, weights, TOP_K);
			List<EvalMetrics.RecommendedItem> recommended = ranked.stream()
					.map(candidate -> toRecommendedItem(candidate, vectors.taste(), weights))
					.toList();
			return new EvalMetrics.MemberEvalResult(vectors.memberId(), vectors.holdoutIds(), recommended, false,
					popularityHitCount(vectors));
		}

		private EvalMetrics.RecommendedItem toRecommendedItem(CandidateVector candidate, TasteSignal taste,
				RecommendWeights weights) {
			ProductFeature feature = candidate.feature();
			boolean tasteMatch = WeightSweep.matchesTaste(feature, taste, scorer, weights);
			return new EvalMetrics.RecommendedItem(feature.id(), feature.albumId(), feature.artistId(),
					feature.labelId(), feature.genreIds(), feature.decade(), tasteMatch);
		}

		/** 위시+구매(홀드아웃 뗀 뒤 남은 소유분)를 뺀 인기순 상위 TOP_K 중 홀드아웃과 겹치는 수. */
		private int popularityHitCount(MemberFoldVectors vectors) {
			return (int)byPopularityIds.stream()
					.filter(id -> !vectors.excludedForPopularity().contains(id))
					.limit(TOP_K)
					.filter(vectors.holdoutIds()::contains)
					.count();
		}

		private record SeedFold(long randomSeed, int foldIndex, List<MemberFoldVectors> members) {
		}
	}
}
