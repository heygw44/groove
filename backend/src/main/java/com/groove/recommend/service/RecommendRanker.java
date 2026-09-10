package com.groove.recommend.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 후보 채점·정렬·앨범 dedup·아티스트/레이블 캡·컷. 홈 추천·관련 상품·평가 하네스가 이 규칙을 공유한다. */
@Component
@RequiredArgsConstructor
public class RecommendRanker {

	private final RecommendScorer recommendScorer;
	private final RecommendWeights weights;

	/** 주입된 기본 가중치로 랭킹한다. */
	public List<RankedCandidate> rank(Map<Long, ProductFeature> features, TasteSignal taste,
			Collection<ProductFeature> seeds, Set<Long> recentOnlySeedIds, Map<Long, Double> coPurchaseScores,
			Set<Long> excludeIds, int size, CapPolicy capPolicy) {
		return rank(features, taste, seeds, recentOnlySeedIds, coPurchaseScores, excludeIds, size, weights,
				capPolicy);
	}

	/** ablation·스윕용 — 주입된 기본 가중치 대신 지정한 가중치로 랭킹한다. */
	public List<RankedCandidate> rank(Map<Long, ProductFeature> features, TasteSignal taste,
			Collection<ProductFeature> seeds, Set<Long> recentOnlySeedIds, Map<Long, Double> coPurchaseScores,
			Set<Long> excludeIds, int size, RecommendWeights rankingWeights, CapPolicy capPolicy) {
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

		List<RankedCandidate> albumDeduped = dedupByAlbum(sorted);
		List<RankedCandidate> picked = pickWithCap(albumDeduped, size, capPolicy);
		if (picked.size() < size) {
			fillRemaining(picked, albumDeduped, size);
		}
		return picked;
	}

	/**
	 * 같은 앨범의 다른 프레싱은 나란히 상위를 차지하므로 앨범당 점수 1위만 남긴다. 캡을 적용할 후보군을 만드는
	 * 단계라 size 로 자르지 않는다 — 여기서 자르면 캡에 걸려 밀린 후보를 pass2 가 이어서 채울 수 없다.
	 */
	private List<RankedCandidate> dedupByAlbum(List<RankedCandidate> sorted) {
		Set<Long> seenAlbumIds = new HashSet<>();
		List<RankedCandidate> deduped = new ArrayList<>();
		for (RankedCandidate candidate : sorted) {
			if (seenAlbumIds.add(candidate.feature().albumId())) {
				deduped.add(candidate);
			}
		}
		return deduped;
	}

	/**
	 * pass1 — 앨범 dedup 리스트를 순서대로 훑으며 같은 아티스트·레이블이 {@code capPolicy} 한도에 닿으면
	 * 건너뛴다. artistId·labelId 가 null 인 후보(레이블은 nullable)는 후보 자신의 id 를 음수로 바꿔 키로 써서,
	 * null 끼리 한 그룹으로 묶여 서로 캡에 걸리지 않게 한다 — 레이블 미상은 같은 레이블이 아니다.
	 */
	private List<RankedCandidate> pickWithCap(List<RankedCandidate> albumDeduped, int size, CapPolicy capPolicy) {
		Map<Long, Integer> artistCounts = new HashMap<>();
		Map<Long, Integer> labelCounts = new HashMap<>();
		List<RankedCandidate> picked = new ArrayList<>(size);
		for (RankedCandidate candidate : albumDeduped) {
			if (picked.size() == size) {
				break;
			}
			ProductFeature feature = candidate.feature();
			long artistKey = keyOrOwnId(feature.artistId(), feature.id());
			long labelKey = keyOrOwnId(feature.labelId(), feature.id());
			int artistCount = artistCounts.getOrDefault(artistKey, 0);
			int labelCount = labelCounts.getOrDefault(labelKey, 0);
			if (artistCount >= capPolicy.maxPerArtist() || labelCount >= capPolicy.maxPerLabel()) {
				continue;
			}
			artistCounts.put(artistKey, artistCount + 1);
			labelCounts.put(labelKey, labelCount + 1);
			picked.add(candidate);
		}
		return picked;
	}

	/**
	 * pass2 — 캡 탓에 size 를 못 채웠으면, 앨범 dedup 리스트를 다시 훑어 캡 없이 남은 자리를 채운다. 시드가
	 * 좁은 회원의 추천이 size 미만으로 짧아지는 것을 막는 안전장치다.
	 */
	private void fillRemaining(List<RankedCandidate> picked, List<RankedCandidate> albumDeduped, int size) {
		Set<Long> pickedIds = new HashSet<>();
		for (RankedCandidate candidate : picked) {
			pickedIds.add(candidate.feature().id());
		}
		for (RankedCandidate candidate : albumDeduped) {
			if (picked.size() == size) {
				break;
			}
			if (pickedIds.add(candidate.feature().id())) {
				picked.add(candidate);
			}
		}
	}

	private long keyOrOwnId(Long dimensionId, Long featureId) {
		return dimensionId != null ? dimensionId : -featureId;
	}

	public record RankedCandidate(ProductFeature feature, RecommendScorer.ScoreResult score) {
	}

	/**
	 * 아티스트·레이블 과점을 막는 캡. pass1 은 같은 키가 이미 {@code maxPerArtist}(또는 {@code maxPerLabel})
	 * 개 뽑혔으면 다음 후보를 건너뛴다. {@link #UNCAPPED} 는 사실상 캡을 걸지 않는다 — 관련 상품 추천처럼
	 * "같은 아티스트의 다른 앨범"이 정당한 추천인 경로에 쓴다.
	 */
	public record CapPolicy(int maxPerArtist, int maxPerLabel) {

		/**
		 * 홈 추천 기본 캡. 사전 측정(0단계) 근거 — maxPerArtist=3 은 baseline occupancy 상 거의 안 걸려
		 * 무의미했고, maxPerArtist=2 라야 maxArtistShare 가 실제로 내려간다. maxPerLabel=3 은 레이블
		 * 편중(baseline distinctLabels/10 0.41)을 완화하면서 슬롯 변경 폭을 과하지 않게 유지한 값이다.
		 */
		public static final CapPolicy HOME = new CapPolicy(2, 3);

		public static final CapPolicy UNCAPPED = new CapPolicy(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}
}
