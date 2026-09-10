package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.groove.member.repository.MemberRepository;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.recommend.service.BoughtTogetherAggregator;
import com.groove.recommend.service.ProductFeature;
import com.groove.recommend.service.ProductFeatureCache;
import com.groove.recommend.service.RecentViewService;
import com.groove.recommend.service.RecommendRanker;
import com.groove.recommend.service.RecommendScorer;
import com.groove.recommend.service.RecommendWeights;
import com.groove.recommend.service.TasteSignal;
import com.groove.recommend.support.CoPurchaseBasket;
import com.groove.recommend.support.CoPurchaseBasketLoader;
import com.groove.recommend.support.CoPurchaseIndex;
import com.groove.recommend.support.EvalMetrics;
import com.groove.recommend.support.EvalReport;
import com.groove.recommend.support.EvalRunner;
import com.groove.recommend.support.EvalSignalLoader;
import com.groove.recommend.support.EvalSignals;
import com.groove.recommend.support.HoldoutKind;
import com.groove.recommend.support.HoldoutSpec;
import com.groove.recommend.support.HoldoutSplitter;
import com.groove.recommend.support.InMemoryCoPurchaseIndex;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.repository.WishlistRepository;

import jakarta.persistence.EntityManager;

/**
 * 추천 점수를 이루는 7개 콘텐츠 차원 + 공동구매의 기여도를 ablation 으로 잰다. 차원 하나씩 가중치를 0으로
 * 만든 구성을 기준({@link RecommendWeights#DEFAULT})과 같은 폴드×시드에서 짝지어 recall Δ 의 분포를 낸다
 * — 절대 recall 의 σ 가 아니라 이 Δ 의 σ 로 판정해야 폴드 난이도 편차가 상쇄된다({@link EvalMetrics#pairedDelta}).
 * {@link RecommendPrecisionTest} 와 같은 컨텍스트 키(local·seed·test)를 써서 스프링 컨텍스트 캐시를
 * 공유한다. 일반 빌드에서는 제외되고 {@code ./gradlew recommendAblation} 으로만 돈다.
 */
@Tag("ablation")
@ActiveProfiles({"local", "seed", "test"})
class RecommendAblationTest extends IntegrationTestSupport {

	private static final int TOP_K = 10;
	private static final int WISH_FOLD_COUNT = 5;
	private static final int PURCHASE_FOLD_COUNT = 3;
	private static final List<Long> RANDOM_SEEDS = List.of(1L, 2L, 3L, 4L, 5L);

	@Autowired
	BoughtTogetherAggregator boughtTogetherAggregator;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	WishlistRepository wishlistRepository;

	@Autowired
	OrderItemRepository orderItemRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	Clock clock;

	@Autowired
	RecommendRanker recommendRanker;

	@Autowired
	RecommendScorer recommendScorer;

	@Autowired
	ProductFeatureCache productFeatureCache;

	@Autowired
	RecentViewService recentViewService;

	@Autowired
	MemberTasteProfileRepository memberTasteProfileRepository;

	@Autowired
	MemberTasteGenreRepository memberTasteGenreRepository;

	@Autowired
	MemberTasteArtistRepository memberTasteArtistRepository;

	@Autowired
	MemberTasteDecadeRepository memberTasteDecadeRepository;

	@Nested
	@DisplayName("recommendHome()")
	class RecommendHome {

		@Test
		@Transactional
		@DisplayName("차원 하나씩 0으로 만든 뒤 기준과 짝지은 Δ÷σ 로 판정하면 콘텐츠 차원별 기여도를 알 수 있다")
		void measuresPerDimensionContribution() throws IOException {
			// given
			boughtTogetherAggregator.refresh();
			Map<Long, ProductFeature> features = productFeatureCache.get();
			List<EvalSignals> seedMembers = newSignalLoader().loadSeedMembers();
			assertThat(seedMembers).isNotEmpty();
			List<CoPurchaseBasket> baskets = CoPurchaseBasketLoader.load(entityManager, clock);

			List<Long> byPopularity = productIdsByPopularity();
			long candidateCount = features.values().stream().filter(feature -> !feature.hidden()).count();
			long albumCandidateCount = features.values().stream()
					.filter(feature -> !feature.hidden())
					.map(ProductFeature::albumId)
					.distinct()
					.count();

			// 홀드아웃 분할은 구성마다 다시 하지 않는다 — 짝지은 비교가 성립하려면 9개 구성이 같은 분할을
			// 공유해야 한다. HoldoutSplitter 는 (kind, foldCount, seed, memberId) 로만 결정되므로
			// 가중치와 무관하게 한 번만 계산해 재사용한다. 공동구매 인덱스도 마찬가지로 (kind, seed, fold) 당
			// 한 번만 재집계해 9개 구성이 공유한다 — 구성별로 다시 만들면 캐싱 범위가 좁아져 다시 누수가 된다.
			HoldoutSplitter splitter = new HoldoutSplitter();
			Map<Long, Map<Long, List<Set<Long>>>> wishFoldsBySeed = foldsBySeed(splitter, HoldoutKind.WISH,
					WISH_FOLD_COUNT, seedMembers);
			Map<Long, Map<Long, List<Set<Long>>>> purchaseFoldsBySeed = foldsBySeed(splitter, HoldoutKind.PURCHASE,
					PURCHASE_FOLD_COUNT, seedMembers);
			Map<Long, List<CoPurchaseIndex>> wishIndexBySeed = coPurchaseIndexBySeed(baskets, wishFoldsBySeed,
					WISH_FOLD_COUNT);
			Map<Long, List<CoPurchaseIndex>> purchaseIndexBySeed = coPurchaseIndexBySeed(baskets, purchaseFoldsBySeed,
					PURCHASE_FOLD_COUNT);

			List<AblationConfig> configs = ablationConfigs();

			// when
			List<EvalMetrics.FoldedRun> wishRuns = new ArrayList<>();
			List<EvalMetrics.FoldedRun> purchaseRuns = new ArrayList<>();
			for (AblationConfig config : configs) {
				wishRuns.add(measureFolded(HoldoutKind.WISH, WISH_FOLD_COUNT, wishFoldsBySeed, wishIndexBySeed,
						seedMembers, byPopularity, features, candidateCount, albumCandidateCount, config.weights()));
				purchaseRuns.add(measureFolded(HoldoutKind.PURCHASE, PURCHASE_FOLD_COUNT, purchaseFoldsBySeed,
						purchaseIndexBySeed, seedMembers, byPopularity, features, candidateCount, albumCandidateCount,
						config.weights()));
			}

			List<String> labels = configs.stream().map(AblationConfig::label).toList();
			String report = EvalReport.renderAblation(labels, wishRuns, purchaseRuns);
			System.out.println(report);
			EvalReport.write(EvalReport.ABLATION_REPORT_PATH, report);

			// then
			assertThat(wishRuns).hasSize(configs.size());
			assertThat(purchaseRuns).hasSize(configs.size());
			assertThat(wishRuns.get(0).recallStats().mean())
					.as("기준 구성의 위시 recall@10 은 0보다 커야 한다")
					.isGreaterThan(0);
			assertThat(purchaseRuns.get(0).recallStats().mean())
					.as("기준 구성의 구매 recall@10 은 0보다 커야 한다")
					.isGreaterThan(0);
		}
	}

	@Nested
	@DisplayName("공동구매 쌍 분포")
	class CoPurchasePairDistribution {

		@Test
		@Transactional
		@DisplayName("주문 바스켓에서 n_ab 히스토그램과 n_a 분포를 낸다")
		void measuresPairDistribution() throws IOException {
			// given
			List<CoPurchaseBasket> baskets = CoPurchaseBasketLoader.load(entityManager, clock);
			assertThat(baskets).isNotEmpty();

			// when
			Map<ProductPair, Long> countByPair = new HashMap<>();
			Map<Long, Long> countByProduct = new HashMap<>();
			long totalItems = 0;
			for (CoPurchaseBasket basket : baskets) {
				List<Long> productIds = basket.productIds().stream().sorted().toList();
				totalItems += productIds.size();
				for (Long productId : productIds) {
					countByProduct.merge(productId, 1L, Long::sum);
				}
				for (int left = 0; left < productIds.size(); left++) {
					for (int right = left + 1; right < productIds.size(); right++) {
						countByPair.merge(new ProductPair(productIds.get(left), productIds.get(right)), 1L,
								Long::sum);
					}
				}
			}

			long pairCountAt1 = countByPair.values().stream().filter(count -> count == 1).count();
			long pairCountAt2 = countByPair.values().stream().filter(count -> count == 2).count();
			long pairCountAtLeast3 = countByPair.values().stream().filter(count -> count >= 3).count();
			List<Long> productAppearances = new ArrayList<>(countByProduct.values());
			Collections.sort(productAppearances);

			String report = EvalReport.renderCoPurchaseDistribution(baskets.size(),
					(double)totalItems / baskets.size(), countByProduct.size(), pairCountAt1, pairCountAt2,
					pairCountAtLeast3, productAppearances.get(0), productAppearances.get(productAppearances.size() - 1),
					median(productAppearances));
			System.out.println(report);
			EvalReport.write(EvalReport.CO_PURCHASE_DISTRIBUTION_REPORT_PATH, report);

			// then
			assertThat(countByPair).isNotEmpty();
		}

		/** 정렬된 값 목록의 중앙값. 개수가 짝수면 가운데 두 값의 평균이다. */
		private double median(List<Long> sortedValues) {
			int size = sortedValues.size();
			if (size % 2 == 1) {
				return sortedValues.get(size / 2);
			}
			return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
		}
	}

	/** 비순서쌍(상품 A, B) 키. {@code left} 는 항상 {@code right} 보다 작은 상품 id다. */
	private record ProductPair(Long left, Long right) {
	}

	/**
	 * 기준 + 7개 콘텐츠 차원 각각 0 + 공동구매 0 + 두 신호군 단독. 순서가 리포트의 행 순서다. 마지막 두 행은
	 * SAME_* 만 남긴 구성(TASTE_* 셋을 0으로)과 TASTE_* 만 남긴 구성(SAME_* 넷을 0으로)이다 — 시드 신호와
	 * 취향 신호가 겹치는지 판단하는 근거다.
	 */
	private List<AblationConfig> ablationConfigs() {
		RecommendWeights base = RecommendWeights.DEFAULT;
		RecommendWeights sameOnly = base.withZeroed(RecommendReason.TASTE_ARTIST)
				.withZeroed(RecommendReason.TASTE_GENRE)
				.withZeroed(RecommendReason.TASTE_DECADE);
		RecommendWeights tasteOnly = base.withZeroed(RecommendReason.SAME_ARTIST)
				.withZeroed(RecommendReason.SAME_GENRE)
				.withZeroed(RecommendReason.SAME_LABEL)
				.withZeroed(RecommendReason.SAME_DECADE);
		return List.of(
				new AblationConfig("(없음, 기준)", base),
				new AblationConfig("TASTE_ARTIST", base.withZeroed(RecommendReason.TASTE_ARTIST)),
				new AblationConfig("SAME_ARTIST", base.withZeroed(RecommendReason.SAME_ARTIST)),
				new AblationConfig("TASTE_GENRE", base.withZeroed(RecommendReason.TASTE_GENRE)),
				new AblationConfig("SAME_GENRE", base.withZeroed(RecommendReason.SAME_GENRE)),
				new AblationConfig("SAME_LABEL", base.withZeroed(RecommendReason.SAME_LABEL)),
				new AblationConfig("TASTE_DECADE", base.withZeroed(RecommendReason.TASTE_DECADE)),
				new AblationConfig("SAME_DECADE", base.withZeroed(RecommendReason.SAME_DECADE)),
				new AblationConfig("BOUGHT_TOGETHER", base.withZeroed(RecommendReason.BOUGHT_TOGETHER)),
				new AblationConfig("SAME_전용(TASTE_* 0)", sameOnly),
				new AblationConfig("TASTE_전용(SAME_* 0)", tasteOnly));
	}

	/** 시드마다 회원별 폴드 분할을 한 번만 계산해 구성 9개가 전부 공유하게 한다. */
	private Map<Long, Map<Long, List<Set<Long>>>> foldsBySeed(HoldoutSplitter splitter, HoldoutKind kind,
			int foldCount, List<EvalSignals> seedMembers) {
		return RANDOM_SEEDS.stream()
				.collect(Collectors.toMap(seed -> seed, seed -> {
					HoldoutSpec spec = new HoldoutSpec(kind, foldCount, seed);
					return seedMembers.stream()
							.collect(Collectors.toMap(EvalSignals::memberId, signals -> splitter.foldsOf(signals,
									spec)));
				}));
	}

	/**
	 * 시드마다 (kind, foldCount) 에 맞는 공동구매 인덱스를 폴드별로 미리 재집계해둔다. {@code foldsBySeed} 로
	 * 이미 계산된 분할에서 회원별 홀드아웃만 뽑아 {@link InMemoryCoPurchaseIndex} 를 만든다 — 9개 ablation
	 * 구성이 이 인덱스를 공유해야 (kind, seed, fold) 당 재집계 1회가 유지된다.
	 */
	private Map<Long, List<CoPurchaseIndex>> coPurchaseIndexBySeed(List<CoPurchaseBasket> baskets,
			Map<Long, Map<Long, List<Set<Long>>>> foldsBySeed, int foldCount) {
		Map<Long, List<CoPurchaseIndex>> indexBySeed = new HashMap<>();
		for (Map.Entry<Long, Map<Long, List<Set<Long>>>> entry : foldsBySeed.entrySet()) {
			Map<Long, List<Set<Long>>> foldsByMemberId = entry.getValue();
			List<CoPurchaseIndex> indexes = new ArrayList<>();
			for (int foldIndex = 0; foldIndex < foldCount; foldIndex++) {
				Map<Long, Set<Long>> holdoutByMemberId = new HashMap<>();
				for (Map.Entry<Long, List<Set<Long>>> memberEntry : foldsByMemberId.entrySet()) {
					holdoutByMemberId.put(memberEntry.getKey(), memberEntry.getValue().get(foldIndex));
				}
				indexes.add(new InMemoryCoPurchaseIndex(baskets, holdoutByMemberId));
			}
			indexBySeed.put(entry.getKey(), indexes);
		}
		return indexBySeed;
	}

	/**
	 * kind 를 폴드 수만큼 등분해 시드마다 전체 폴드를 한 바퀴 돈다. 측정 수는 {@code foldCount * 시드 수}다.
	 * {@code weights} 만 구성마다 바뀌고, 홀드아웃 분할과 공동구매 인덱스는 미리 계산된 것을 그대로 쓴다.
	 */
	private EvalMetrics.FoldedRun measureFolded(HoldoutKind kind, int foldCount,
			Map<Long, Map<Long, List<Set<Long>>>> foldsBySeed, Map<Long, List<CoPurchaseIndex>> indexBySeed,
			List<EvalSignals> seedMembers, List<Long> byPopularity, Map<Long, ProductFeature> features,
			long candidateCount, long albumCandidateCount, RecommendWeights weights) {
		List<EvalMetrics.Measurement> measurements = new ArrayList<>();

		for (Long seed : RANDOM_SEEDS) {
			Map<Long, List<Set<Long>>> foldsByMemberId = foldsBySeed.get(seed);
			List<CoPurchaseIndex> indexesByFold = indexBySeed.get(seed);
			for (int foldIndex = 0; foldIndex < foldCount; foldIndex++) {
				EvalRunner runner = new EvalRunner(recommendRanker, indexesByFold.get(foldIndex), features);
				List<EvalMetrics.MemberEvalResult> results = new ArrayList<>();
				for (EvalSignals signals : seedMembers) {
					Set<Long> holdout = foldsByMemberId.get(signals.memberId()).get(foldIndex);
					results.add(measure(signals, holdout, byPopularity, runner, weights));
				}
				EvalMetrics.Summary summary = EvalMetrics.summarize(results, candidateCount, albumCandidateCount);
				measurements.add(new EvalMetrics.Measurement(seed, foldIndex, summary));
			}
		}

		return new EvalMetrics.FoldedRun(kind, foldCount, RANDOM_SEEDS, measurements);
	}

	private EvalMetrics.MemberEvalResult measure(EvalSignals signals, Set<Long> holdout, List<Long> byPopularity,
			EvalRunner runner, RecommendWeights weights) {
		if (holdout.isEmpty()) {
			return EvalMetrics.MemberEvalResult.empty(signals.memberId());
		}

		EvalSignals foldSignals = signals.without(holdout);
		EvalRunner.Result result = runner.recommend(foldSignals, TOP_K, weights);
		List<EvalMetrics.RecommendedItem> recommended = result.ranked().stream()
				.map(candidate -> toRecommendedItem(candidate.feature(), signals.taste()))
				.toList();

		List<Long> popularityPicks = popularityTopK(foldSignals, byPopularity);
		int popularityHitCount = (int)popularityPicks.stream().filter(holdout::contains).count();

		return new EvalMetrics.MemberEvalResult(signals.memberId(), holdout, recommended, result.fallback(),
				popularityHitCount);
	}

	private EvalMetrics.RecommendedItem toRecommendedItem(ProductFeature feature, TasteSignal taste) {
		RecommendScorer.ScoreResult tasteScore = recommendScorer.scoreTaste(feature, taste);
		boolean tasteMatch = recommendScorer.matchesTaste(tasteScore);
		return new EvalMetrics.RecommendedItem(feature.id(), feature.albumId(), feature.artistId(),
				feature.labelId(), feature.genreIds(), feature.decade(), tasteMatch);
	}

	/** 대조군 계산에 쓸 후보 제외 기준. {@link RecommendPrecisionTest} 와 같은 방식이다. */
	private List<Long> popularityTopK(EvalSignals foldSignals, List<Long> byPopularity) {
		Set<Long> excluded = new HashSet<>(foldSignals.wishedIds());
		excluded.addAll(foldSignals.purchasedIds());
		return byPopularity.stream()
				.filter(id -> !excluded.contains(id))
				.limit(TOP_K)
				.toList();
	}

	/** 평점 내림차순 → 최신순 → id 내림차순. RecommendService 의 동점 처리와 같은 순서다. */
	private List<Long> productIdsByPopularity() {
		return entityManager.createQuery(
						"select p.id from Product p where p.status <> :hidden "
								+ "order by p.averageRating desc, p.createdAt desc, p.id desc", Long.class)
				.setParameter("hidden", ProductStatus.HIDDEN)
				.getResultList();
	}

	private EvalSignalLoader newSignalLoader() {
		return new EvalSignalLoader(memberRepository, wishlistRepository, orderItemRepository, recentViewService,
				memberTasteProfileRepository, memberTasteGenreRepository, memberTasteArtistRepository,
				memberTasteDecadeRepository);
	}

	/** ablation 구성 하나. {@code label} 은 리포트 행 이름이다. */
	private record AblationConfig(String label, RecommendWeights weights) {
	}
}
