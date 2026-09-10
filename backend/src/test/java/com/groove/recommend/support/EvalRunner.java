package com.groove.recommend.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.groove.recommend.service.HomeSeeds;
import com.groove.recommend.service.ProductFeature;
import com.groove.recommend.service.RecommendRanker;
import com.groove.recommend.service.RecommendWeights;

/**
 * {@code RecommendService.recommendHome()} 의 랭킹 경로를 그대로 재현하는 인메모리 러너. 스프링 빈이
 * 아니라 평범한 클래스다 — 상품 특성 스냅샷을 생성자에서 한 번 받아 두고, 그 뒤로는 DB/Redis 상품 조회를
 * 다시 하지 않는다. 공동구매 점수는 {@link CoPurchaseIndex} 로 갈아 끼운다 — 운영 경로 검증에는
 * {@link RedisCoPurchaseIndex}, 홀드아웃 누수 없는 측정에는 {@link InMemoryCoPurchaseIndex} 를 쓴다.
 */
public class EvalRunner {

	private final RecommendRanker recommendRanker;
	private final CoPurchaseIndex coPurchaseIndex;
	private final Map<Long, ProductFeature> features;

	public EvalRunner(RecommendRanker recommendRanker, CoPurchaseIndex coPurchaseIndex,
			Map<Long, ProductFeature> features) {
		this.recommendRanker = recommendRanker;
		this.coPurchaseIndex = coPurchaseIndex;
		this.features = features;
	}

	/** 주입된 기본 가중치로 추천한다. */
	public Result recommend(EvalSignals signals, int size) {
		return recommend(signals, size, null);
	}

	/**
	 * 지정한 가중치로 추천한다({@code weights} 가 null 이면 기본 가중치). 가중치를 바꿔가며 재평가하는
	 * 후속 측정(ablation·스윕)이 이 오버로드를 쓴다.
	 *
	 * <p>{@code recommendHome()} 은 취향·시드가 둘 다 비면 인기순 폴백을 타지만, 이 러너는
	 * {@link RecommendRanker} 를 직접 부르므로 그 분기가 없다. 대신 폴백 조건을 스스로 판별해
	 * {@link Result#fallback()} 으로 알린다 — 호출부가 폴백 회원 수를 리포트에 남기는 안전장치다.
	 */
	public Result recommend(EvalSignals signals, int size, RecommendWeights weights) {
		return recommend(signals, size, weights, RecommendRanker.CapPolicy.HOME);
	}

	/**
	 * 가중치·캡 정책을 모두 지정해 추천한다. {@code capPolicy} 를 명시적으로 바꿔가며 캡 도입 전/후를
	 * 짝짓는 측정이 이 오버로드를 쓴다.
	 */
	public Result recommend(EvalSignals signals, int size, RecommendWeights weights,
			RecommendRanker.CapPolicy capPolicy) {
		HomeSeeds homeSeeds = HomeSeeds.of(signals.wishedIds(), signals.purchasedIds(), signals.recentIds());
		Set<Long> seedIds = homeSeeds.seedIds();

		if (signals.taste().isEmpty() && seedIds.isEmpty()) {
			return Result.popularFallback();
		}

		List<ProductFeature> seeds = seedIds.stream()
				.map(features::get)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, Double> coPurchaseScores = aggregateCoPurchaseScores(seedIds);

		List<RecommendRanker.RankedCandidate> ranked = weights == null
				? recommendRanker.rank(features, signals.taste(), seeds, homeSeeds.recentOnlySeedIds(),
						coPurchaseScores, seedIds, size, capPolicy)
				: recommendRanker.rank(features, signals.taste(), seeds, homeSeeds.recentOnlySeedIds(),
						coPurchaseScores, seedIds, size, weights, capPolicy);

		return Result.of(ranked);
	}

	/** {@code RecommendService.aggregateCoPurchaseScores()} 와 동일 로직. */
	private Map<Long, Double> aggregateCoPurchaseScores(Set<Long> seedIds) {
		if (seedIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Double> totalScores = new LinkedHashMap<>();
		for (Map<Long, Double> scoresByCandidate : coPurchaseIndex.findScores(seedIds).values()) {
			scoresByCandidate.forEach((candidateId, score) -> totalScores.merge(candidateId, score, Double::sum));
		}
		return totalScores;
	}

	public record Result(List<RecommendRanker.RankedCandidate> ranked, boolean fallback) {

		static Result of(List<RecommendRanker.RankedCandidate> ranked) {
			return new Result(ranked, false);
		}

		static Result popularFallback() {
			return new Result(List.of(), true);
		}

		public List<Long> productIds() {
			return ranked.stream().map(candidate -> candidate.feature().id()).toList();
		}
	}
}
