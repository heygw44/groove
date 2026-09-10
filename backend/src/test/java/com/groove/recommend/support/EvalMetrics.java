package com.groove.recommend.support;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.groove.recommend.entity.Decade;
import com.groove.recommend.service.ProductFeature;

/**
 * 추천 품질 지표 계산. 외부 통계 라이브러리 없이 순수 자바로 recall/precision/hit-rate, 순위 민감
 * 지표(nDCG·MAP), 인기 편향(Gini)과 다양성, 취향 매칭률을 낸다.
 */
public final class EvalMetrics {

	private static final int TOP_K = 10;

	private EvalMetrics() {
	}

	public static Summary summarize(List<MemberEvalResult> results, long candidateCount, long albumCandidateCount) {
		List<MemberEvalResult> evaluable = results.stream().filter(r -> !r.holdoutIds().isEmpty()).toList();
		List<MemberEvalResult> withRecommendations = results.stream()
				.filter(r -> !r.recommended().isEmpty())
				.toList();

		int memberCount = results.size();
		int holdoutTotal = evaluable.stream().mapToInt(r -> r.holdoutIds().size()).sum();
		int hitTotal = evaluable.stream().mapToInt(MemberEvalResult::hitCount).sum();
		int hitMemberCount = (int)evaluable.stream().filter(r -> r.hitCount() > 0).count();
		int popularityHitTotal = evaluable.stream().mapToInt(MemberEvalResult::popularityHitCount).sum();
		int fallbackCount = (int)results.stream().filter(MemberEvalResult::fallback).count();

		double recallMacro = evaluable.isEmpty() ? 0
				: evaluable.stream().mapToDouble(r -> (double)r.hitCount() / r.holdoutIds().size()).average()
						.orElse(0);
		double ndcg = evaluable.isEmpty() ? 0
				: evaluable.stream().mapToDouble(MemberEvalResult::ndcg).average().orElse(0);
		double map = evaluable.isEmpty() ? 0
				: evaluable.stream().mapToDouble(MemberEvalResult::averagePrecision).average().orElse(0);

		Diversity diversity = Diversity.average(withRecommendations);

		Set<Long> recommendedProductIds = results.stream()
				.flatMap(r -> r.recommended().stream())
				.map(RecommendedItem::productId)
				.collect(Collectors.toSet());
		Set<Long> recommendedAlbumIds = results.stream()
				.flatMap(r -> r.recommended().stream())
				.map(RecommendedItem::albumId)
				.collect(Collectors.toSet());
		double productCoverage = candidateCount == 0 ? 0 : (double)recommendedProductIds.size() / candidateCount;
		double albumCoverage = albumCandidateCount == 0 ? 0
				: (double)recommendedAlbumIds.size() / albumCandidateCount;

		Map<Long, Long> countsByProductId = results.stream()
				.flatMap(r -> r.recommended().stream())
				.collect(Collectors.groupingBy(RecommendedItem::productId, Collectors.counting()));
		double gini = gini(countsByProductId, candidateCount);

		long tasteMatches = results.stream().flatMap(r -> r.recommended().stream())
				.filter(RecommendedItem::tasteMatch)
				.count();
		long totalRecommended = results.stream().mapToLong(r -> r.recommended().size()).sum();
		double tasteMatchRate = totalRecommended == 0 ? 0 : (double)tasteMatches / totalRecommended;

		double randomBaseline = candidateCount == 0 ? 0 : (double)TOP_K / candidateCount;
		double recallMicro = holdoutTotal == 0 ? 0 : (double)hitTotal / holdoutTotal;
		double precision = memberCount == 0 ? 0 : (double)hitTotal / ((long)memberCount * TOP_K);
		double hitRate = memberCount == 0 ? 0 : (double)hitMemberCount / memberCount;
		double popularityRecall = holdoutTotal == 0 ? 0 : (double)popularityHitTotal / holdoutTotal;

		return new Summary(memberCount, evaluable.size(), candidateCount, albumCandidateCount, holdoutTotal, hitTotal,
				hitMemberCount, popularityHitTotal, fallbackCount, recallMicro, recallMacro, precision, hitRate,
				popularityRecall, randomBaseline, ndcg, map, productCoverage, albumCoverage, gini, diversity,
				tasteMatchRate);
	}

	/** 상품별 추천 등장 횟수 분포의 Gini 계수. n(카탈로그 전체) 중 추천에 한 번도 안 뽑힌 상품은 0건으로 채운다. */
	private static double gini(Map<Long, Long> countsByProductId, long candidateCount) {
		if (candidateCount == 0) {
			return 0;
		}
		double[] counts = new double[(int)candidateCount];
		int cursor = 0;
		for (long count : countsByProductId.values()) {
			counts[cursor++] = count;
		}
		Arrays.sort(counts);
		double sum = 0;
		for (double count : counts) {
			sum += count;
		}
		if (sum == 0) {
			return 0;
		}
		int itemCount = counts.length;
		double numerator = 0;
		for (int idx = 0; idx < itemCount; idx++) {
			int rank = idx + 1;
			numerator += (2.0 * rank - itemCount - 1) * counts[idx];
		}
		return numerator / (itemCount * sum);
	}

	/** 후보 풀(비HIDDEN 상품)의 장르별 상품 수 분포. 분포가 얼마나 평평한지로 후속 IDF 도입 여부를 판단한다. */
	public static List<GenreDf> genreDocumentFrequency(Map<Long, ProductFeature> features,
			Map<Long, String> genreNames) {
		Map<Long, Long> countByGenreId = features.values().stream()
				.filter(feature -> !feature.hidden())
				.flatMap(feature -> feature.genreIds().stream())
				.collect(Collectors.groupingBy(genreId -> genreId, Collectors.counting()));
		return countByGenreId.entrySet().stream()
				.map(entry -> new GenreDf(entry.getKey(), genreNames.getOrDefault(entry.getKey(), "알 수 없음"),
						entry.getValue()))
				.sorted(Comparator.comparingLong(GenreDf::productCount).reversed())
				.toList();
	}

	/** 회원 한 명의 홀드아웃·추천 결과. */
	public record MemberEvalResult(Long memberId, Set<Long> holdoutIds, List<RecommendedItem> recommended,
			boolean fallback, int popularityHitCount) {

		public static MemberEvalResult empty(Long memberId) {
			return new MemberEvalResult(memberId, Set.of(), List.of(), false, 0);
		}

		private List<Long> recommendedIds() {
			return recommended.stream().map(RecommendedItem::productId).toList();
		}

		int hitCount() {
			return (int)recommendedIds().stream().filter(holdoutIds::contains).count();
		}

		/** DCG = Σ rel_i/log2(i+1), IDCG = Σ_{i=1..min(|H|,10)} 1/log2(i+1). */
		double ndcg() {
			List<Long> ids = recommendedIds();
			double dcg = 0;
			for (int i = 0; i < ids.size(); i++) {
				if (holdoutIds.contains(ids.get(i))) {
					dcg += 1.0 / log2(i + 2);
				}
			}
			int idealHits = Math.min(holdoutIds.size(), TOP_K);
			double idcg = 0;
			for (int i = 0; i < idealHits; i++) {
				idcg += 1.0 / log2(i + 2);
			}
			return idcg == 0 ? 0 : dcg / idcg;
		}

		/** AP = (1/min(|H|,10)) Σ_{i:rel_i=1} P@i. */
		double averagePrecision() {
			List<Long> ids = recommendedIds();
			int hits = 0;
			double sumPrecision = 0;
			for (int i = 0; i < ids.size(); i++) {
				if (holdoutIds.contains(ids.get(i))) {
					hits++;
					sumPrecision += (double)hits / (i + 1);
				}
			}
			int denominator = Math.min(holdoutIds.size(), TOP_K);
			return denominator == 0 ? 0 : sumPrecision / denominator;
		}

		private static double log2(double value) {
			return Math.log(value) / Math.log(2);
		}
	}

	/** 추천 후보 한 건. 다양성·취향 매칭률 계산에 필요한 특성만 담는다. */
	public record RecommendedItem(Long productId, Long albumId, Long artistId, Long labelId, Set<Long> genreIds,
			Decade decade, boolean tasteMatch) {
	}

	/** 장르별 상품 수 한 줄. */
	public record GenreDf(Long genreId, String genreName, long productCount) {
	}

	/** 리스트 내부 다양성. distinctX 계열은 목표 슬레이트 크기({@value #TOP_K})로 나눠 "얼마나 채웠는지"를 본다. */
	public record Diversity(double distinctArtistsRatio, double distinctLabelsRatio, double distinctDecadesRatio,
			double maxArtistShare, double genreJaccardDistance) {

		private static final Diversity EMPTY = new Diversity(0, 0, 0, 0, 0);

		static Diversity average(List<MemberEvalResult> withRecommendations) {
			if (withRecommendations.isEmpty()) {
				return EMPTY;
			}
			List<Diversity> perMember = withRecommendations.stream().map(Diversity::of).toList();
			int memberCount = perMember.size();
			return new Diversity(
					perMember.stream().mapToDouble(Diversity::distinctArtistsRatio).sum() / memberCount,
					perMember.stream().mapToDouble(Diversity::distinctLabelsRatio).sum() / memberCount,
					perMember.stream().mapToDouble(Diversity::distinctDecadesRatio).sum() / memberCount,
					perMember.stream().mapToDouble(Diversity::maxArtistShare).sum() / memberCount,
					perMember.stream().mapToDouble(Diversity::genreJaccardDistance).sum() / memberCount);
		}

		private static Diversity of(MemberEvalResult result) {
			List<RecommendedItem> items = result.recommended();
			int size = items.size();
			long distinctArtists = items.stream().map(RecommendedItem::artistId).distinct().count();
			long distinctLabels = items.stream().map(RecommendedItem::labelId).distinct().count();
			long distinctDecades = items.stream().map(RecommendedItem::decade).distinct().count();
			Map<Long, Long> artistCounts = items.stream()
					.collect(Collectors.groupingBy(RecommendedItem::artistId, Collectors.counting()));
			double maxArtistShare = artistCounts.values().stream().mapToLong(Long::longValue).max().orElse(0)
					/ (double)size;
			return new Diversity(distinctArtists / (double)TOP_K, distinctLabels / (double)TOP_K,
					distinctDecades / (double)TOP_K, maxArtistShare, pairwiseGenreJaccardDistance(items));
		}

		/** 후보 쌍마다 1 - Jaccard 유사도(교집합/합집합)를 구해 평균한다. 둘 다 장르가 없으면 그 쌍은 건너뛴다. */
		private static double pairwiseGenreJaccardDistance(List<RecommendedItem> items) {
			int itemCount = items.size();
			if (itemCount < 2) {
				return 0;
			}
			double sum = 0;
			int pairs = 0;
			for (int i = 0; i < itemCount; i++) {
				for (int j = i + 1; j < itemCount; j++) {
					Set<Long> genreIdsLeft = items.get(i).genreIds();
					Set<Long> genreIdsRight = items.get(j).genreIds();
					if (genreIdsLeft.isEmpty() && genreIdsRight.isEmpty()) {
						continue;
					}
					Set<Long> union = new HashSet<>(genreIdsLeft);
					union.addAll(genreIdsRight);
					Set<Long> intersection = new HashSet<>(genreIdsLeft);
					intersection.retainAll(genreIdsRight);
					sum += 1 - (double)intersection.size() / union.size();
					pairs++;
				}
			}
			return pairs == 0 ? 0 : sum / pairs;
		}
	}

	/** 측정 회원 전체를 집계한 결과 묶음. */
	public record Summary(int memberCount, int evaluableMemberCount, long candidateCount, long albumCandidateCount,
			int holdoutTotal, int hitTotal, int hitMemberCount, int popularityHitTotal, int fallbackCount,
			double recallMicro, double recallMacro, double precisionAtK, double hitRateAtK, double popularityRecall,
			double randomBaseline, double ndcg, double map, double productCoverage, double albumCoverage,
			double gini, Diversity diversity, double tasteMatchRate) {

		public double vsRandom() {
			return randomBaseline == 0 ? 0 : recallMicro / randomBaseline;
		}

		public double vsPopularity() {
			return popularityRecall == 0 ? 0 : recallMicro / popularityRecall;
		}
	}
}
