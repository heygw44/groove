package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
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
import com.groove.recommend.service.BoughtTogetherRedisService;
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
import com.groove.recommend.support.RedisCoPurchaseIndex;
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
	private static final Path DIVERSITY_CAP_REPORT_PATH = Path.of("build", "reports", "recommend-eval",
			"diversity-cap-decision.md");

	@Autowired
	BoughtTogetherAggregator boughtTogetherAggregator;

	@Autowired
	BoughtTogetherRedisService boughtTogetherRedisService;

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

	/**
	 * 추천 다양성(아티스트·레이블 캡) 0단계 측정 + 후보 비교 + 실제 구현 검증. 0단계·후보 비교는
	 * {@code RecommendRanker} 를 거치지 않고 rank() 가 만드는 넓은 후보 풀(POOL_SIZE, 캡 없이 조회) 위에서
	 * 로컬 시뮬레이션으로 캡 값을 정한 근거다. 채택된 값(maxPerArtist=2, maxPerLabel=3)은
	 * {@link RecommendRanker.CapPolicy#HOME} 으로 실제 구현됐고, 마지막 검증 단계가 이 시뮬레이션 값과
	 * {@code CapPolicy.HOME} 으로 실제 랭킹한 값이 일치하는지 확인한다 — 다르면 시뮬레이션이 실제 랭킹
	 * 경로를 정확히 재현하지 못했다는 뜻이다.
	 */
	@Nested
	@DisplayName("다양성 캡 0단계 측정 · 후보 비교 · 실제 구현 검증")
	class DiversityCapDecision {

		private static final int POOL_SIZE = 50;
		private static final CapCandidate ADOPTED_CANDIDATE = new CapCandidate(2, 3);

		@Test
		@Transactional
		@DisplayName("캡 값(2/3/4)별 적중 건수·점유 분포를 재고, 후보 캡 도입 전후를 같은 폴드에서 짝지어 recall Δ·다양성 지표를 비교한다")
		void measuresCapValueImpactAndComparesCandidates() throws IOException {
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

			// when — 0단계: 캡 값별 적중 건수 + 점유 분포(홀드아웃 없는 전체 신호 기준)
			CapImpactStats stats = measureCapImpact(features, seedMembers);

			// when — 캡 후보 도입 전후 paired 비교(같은 폴드 분할·공동구매 인덱스를 baseline·후보가 공유한다)
			HoldoutSplitter splitter = new HoldoutSplitter();
			Map<Long, Map<Long, List<Set<Long>>>> wishFoldsBySeed = foldsBySeed(splitter, HoldoutKind.WISH,
					WISH_FOLD_COUNT, seedMembers);
			Map<Long, Map<Long, List<Set<Long>>>> purchaseFoldsBySeed = foldsBySeed(splitter, HoldoutKind.PURCHASE,
					PURCHASE_FOLD_COUNT, seedMembers);
			Map<Long, List<CoPurchaseIndex>> wishIndexBySeed = coPurchaseIndexBySeed(baskets, wishFoldsBySeed,
					WISH_FOLD_COUNT);
			Map<Long, List<CoPurchaseIndex>> purchaseIndexBySeed = coPurchaseIndexBySeed(baskets,
					purchaseFoldsBySeed, PURCHASE_FOLD_COUNT);

			// baseline 은 캡 도입 전(UNCAPPED) 상태를 명시적으로 요청한다 — EvalRunner 기본값(HOME)에 기대면
			// "캡 없음" 을 재현하지 못한다.
			EvalMetrics.FoldedRun wishBaseline = measureFoldedExplicit(HoldoutKind.WISH, WISH_FOLD_COUNT,
					wishFoldsBySeed, wishIndexBySeed, seedMembers, byPopularity, features, candidateCount,
					albumCandidateCount, RecommendWeights.DEFAULT, RecommendRanker.CapPolicy.UNCAPPED);
			EvalMetrics.FoldedRun purchaseBaseline = measureFoldedExplicit(HoldoutKind.PURCHASE, PURCHASE_FOLD_COUNT,
					purchaseFoldsBySeed, purchaseIndexBySeed, seedMembers, byPopularity, features, candidateCount,
					albumCandidateCount, RecommendWeights.DEFAULT, RecommendRanker.CapPolicy.UNCAPPED);

			List<CapCandidate> candidates = List.of(ADOPTED_CANDIDATE, new CapCandidate(1, 3));
			List<String> report = new ArrayList<>();
			report.add(renderCapImpact(stats));

			EvalMetrics.FoldedRun wishSimulatedHome = null;
			EvalMetrics.FoldedRun purchaseSimulatedHome = null;
			for (CapCandidate candidate : candidates) {
				EvalMetrics.FoldedRun wishCapped = measureFoldedCapped(HoldoutKind.WISH, WISH_FOLD_COUNT,
						wishFoldsBySeed, wishIndexBySeed, seedMembers, byPopularity, features, candidateCount,
						albumCandidateCount, candidate);
				EvalMetrics.FoldedRun purchaseCapped = measureFoldedCapped(HoldoutKind.PURCHASE, PURCHASE_FOLD_COUNT,
						purchaseFoldsBySeed, purchaseIndexBySeed, seedMembers, byPopularity, features, candidateCount,
						albumCandidateCount, candidate);
				EvalMetrics.MeasurementStats wishDelta = EvalMetrics.pairedDelta(wishBaseline, wishCapped);
				EvalMetrics.MeasurementStats purchaseDelta = EvalMetrics.pairedDelta(purchaseBaseline, purchaseCapped);
				report.add(renderCandidateComparison(candidate, wishBaseline, wishCapped, wishDelta,
						purchaseBaseline, purchaseCapped, purchaseDelta));

				if (candidate.equals(ADOPTED_CANDIDATE)) {
					wishSimulatedHome = wishCapped;
					purchaseSimulatedHome = purchaseCapped;
				}
			}

			// when — 채택된 값(2,3)은 실제 RecommendRanker.CapPolicy.HOME 으로도 측정해 시뮬레이션과 일치하는지
			// 검증한다. 이게 이번 구현의 핵심 검증이다 — 다르면 시뮬레이션이 실제 랭킹 경로를 못 재현한 것이다.
			EvalMetrics.FoldedRun wishReal = measureFoldedExplicit(HoldoutKind.WISH, WISH_FOLD_COUNT, wishFoldsBySeed,
					wishIndexBySeed, seedMembers, byPopularity, features, candidateCount, albumCandidateCount,
					RecommendWeights.DEFAULT, RecommendRanker.CapPolicy.HOME);
			EvalMetrics.FoldedRun purchaseReal = measureFoldedExplicit(HoldoutKind.PURCHASE, PURCHASE_FOLD_COUNT,
					purchaseFoldsBySeed, purchaseIndexBySeed, seedMembers, byPopularity, features, candidateCount,
					albumCandidateCount, RecommendWeights.DEFAULT, RecommendRanker.CapPolicy.HOME);
			report.add(renderSimulationVsReal(wishSimulatedHome, wishReal, purchaseSimulatedHome, purchaseReal));

			String fullReport = String.join("\n", report);
			System.out.println(fullReport);
			EvalReport.write(DIVERSITY_CAP_REPORT_PATH, fullReport);

			// then — 측정 로직이 정상 동작했는지 + 채택된 캡의 시뮬레이션과 실제 구현이 일치하는지 확인한다.
			// 채택/기각 판정 자체는 리포트 수치로 사람이 내린다.
			assertThat(stats.evaluatedMembers()).isGreaterThan(0);
			assertThat(wishBaseline.measurements()).isNotEmpty();
			assertThat(purchaseBaseline.measurements()).isNotEmpty();
			assertSameDiversityMetrics(wishSimulatedHome, wishReal);
			assertSameDiversityMetrics(purchaseSimulatedHome, purchaseReal);
		}

		/** 시뮬레이션과 실제 구현이 회원·폴드별로 정확히 같은 추천을 냈는지 recall·다양성 지표로 확인한다. */
		private void assertSameDiversityMetrics(EvalMetrics.FoldedRun simulated, EvalMetrics.FoldedRun real) {
			assertThat(real.recallStats().mean())
					.as("시뮬레이션과 실제 구현의 recall@10 평균이 같아야 한다")
					.isEqualTo(simulated.recallStats().mean());
			assertThat(meanMaxArtistShare(real))
					.as("시뮬레이션과 실제 구현의 maxArtistShare 평균이 같아야 한다")
					.isEqualTo(meanMaxArtistShare(simulated));
			assertThat(meanCoverage(real))
					.as("시뮬레이션과 실제 구현의 coverage@10 평균이 같아야 한다")
					.isEqualTo(meanCoverage(simulated));
			assertThat(real.totalShortRecommendationOccurrences())
					.as("시뮬레이션과 실제 구현의 추천 10개 미만 발생 횟수가 같아야 한다")
					.isEqualTo(simulated.totalShortRecommendationOccurrences());
		}

		/** 캡 값(2/3/4)별 적중 건수와 baseline top10 의 아티스트·레이블 점유 분포. 홀드아웃 없는 전체 신호 기준. */
		private CapImpactStats measureCapImpact(Map<Long, ProductFeature> features, List<EvalSignals> seedMembers) {
			Map<Integer, Long> artistHitsByK = new HashMap<>();
			Map<Integer, Long> labelHitsByK = new HashMap<>();
			Map<Integer, Integer> artistOccupancy = new HashMap<>();
			Map<Integer, Integer> labelOccupancy = new HashMap<>();
			int evaluatedMembers = 0;

			EvalRunner runner = new EvalRunner(recommendRanker, new RedisCoPurchaseIndex(boughtTogetherRedisService),
					features);
			for (EvalSignals signals : seedMembers) {
				// 0단계는 "캡을 걸면 몇 건이나 밀려나는가" 를 재는 것이므로 자연 순위(캡 없음)를 봐야 한다.
				EvalRunner.Result result = runner.recommend(signals, POOL_SIZE, RecommendWeights.DEFAULT,
						RecommendRanker.CapPolicy.UNCAPPED);
				if (result.fallback() || result.ranked().isEmpty()) {
					continue;
				}
				evaluatedMembers++;
				List<ProductFeature> top10 = result.ranked().stream()
						.limit(TOP_K)
						.map(RecommendRanker.RankedCandidate::feature)
						.toList();
				for (int k : List.of(2, 3, 4)) {
					artistHitsByK.merge(k, countCapHits(result.ranked(), true, k), Long::sum);
					labelHitsByK.merge(k, countCapHits(result.ranked(), false, k), Long::sum);
				}
				artistOccupancy.merge(Math.min(maxShare(top10, true), 4), 1, Integer::sum);
				labelOccupancy.merge(Math.min(maxShare(top10, false), 4), 1, Integer::sum);
			}
			return new CapImpactStats(evaluatedMembers, artistHitsByK, labelHitsByK, artistOccupancy, labelOccupancy);
		}

		/**
		 * pool(정렬·앨범 dedup 된 리스트)을 순서대로 훑으며 같은 아티스트(또는 레이블)가 k 개를 넘으면 건너뛴
		 * 건수를 센다. artistId·labelId 가 null 인 후보는 서로 다른 그룹으로 취급한다(후보 자신의 id 를 음수로
		 * 써서 null 끼리 뭉치지 않게 한다).
		 */
		private long countCapHits(List<RecommendRanker.RankedCandidate> pool, boolean byArtist, int capLimit) {
			Map<Long, Integer> countByKey = new HashMap<>();
			long hits = 0;
			for (RecommendRanker.RankedCandidate candidate : pool) {
				long key = keyOf(candidate.feature(), byArtist);
				int count = countByKey.getOrDefault(key, 0);
				if (count >= capLimit) {
					hits++;
					continue;
				}
				countByKey.put(key, count + 1);
			}
			return hits;
		}

		private int maxShare(List<ProductFeature> top10, boolean byArtist) {
			Map<Long, Long> counts = top10.stream()
					.filter(feature -> (byArtist ? feature.artistId() : feature.labelId()) != null)
					.collect(Collectors.groupingBy(feature -> keyOf(feature, byArtist), Collectors.counting()));
			return (int)counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
		}

		private long keyOf(ProductFeature feature, boolean byArtist) {
			Long dimensionId = byArtist ? feature.artistId() : feature.labelId();
			return dimensionId != null ? dimensionId : -feature.id();
		}

		/**
		 * pass1(아티스트·레이블 캡 동시 적용) + pass2(캡 없이 나머지 채움)로 pool 에서 상위 size 개를 뽑는다.
		 * {@code RecommendRanker.rank()} 에 넣을 2패스 캡 알고리즘과 동일하되, 여기서는 이미 계산된 pool 위에서
		 * 시뮬레이션만 한다.
		 */
		private List<RecommendRanker.RankedCandidate> capTopK(List<RecommendRanker.RankedCandidate> pool,
				CapCandidate candidate, int size) {
			Map<Long, Integer> artistCounts = new HashMap<>();
			Map<Long, Integer> labelCounts = new HashMap<>();
			List<RecommendRanker.RankedCandidate> picked = new ArrayList<>(size);
			for (RecommendRanker.RankedCandidate ranked : pool) {
				if (picked.size() == size) {
					break;
				}
				long artistKey = keyOf(ranked.feature(), true);
				long labelKey = keyOf(ranked.feature(), false);
				int artistCount = artistCounts.getOrDefault(artistKey, 0);
				int labelCount = labelCounts.getOrDefault(labelKey, 0);
				if (artistCount >= candidate.maxPerArtist() || labelCount >= candidate.maxPerLabel()) {
					continue;
				}
				artistCounts.put(artistKey, artistCount + 1);
				labelCounts.put(labelKey, labelCount + 1);
				picked.add(ranked);
			}
			if (picked.size() < size) {
				Set<Long> pickedIds = picked.stream()
						.map(ranked -> ranked.feature().id())
						.collect(Collectors.toCollection(HashSet::new));
				for (RecommendRanker.RankedCandidate ranked : pool) {
					if (picked.size() == size) {
						break;
					}
					if (pickedIds.add(ranked.feature().id())) {
						picked.add(ranked);
					}
				}
			}
			return picked;
		}

		private EvalMetrics.FoldedRun measureFoldedCapped(HoldoutKind kind, int foldCount,
				Map<Long, Map<Long, List<Set<Long>>>> foldsBySeed, Map<Long, List<CoPurchaseIndex>> indexBySeed,
				List<EvalSignals> seedMembers, List<Long> byPopularity, Map<Long, ProductFeature> features,
				long candidateCount, long albumCandidateCount, CapCandidate candidate) {
			List<EvalMetrics.Measurement> measurements = new ArrayList<>();
			for (Long seed : RANDOM_SEEDS) {
				Map<Long, List<Set<Long>>> foldsByMemberId = foldsBySeed.get(seed);
				List<CoPurchaseIndex> indexesByFold = indexBySeed.get(seed);
				for (int foldIndex = 0; foldIndex < foldCount; foldIndex++) {
					EvalRunner runner = new EvalRunner(recommendRanker, indexesByFold.get(foldIndex), features);
					List<EvalMetrics.MemberEvalResult> results = new ArrayList<>();
					for (EvalSignals signals : seedMembers) {
						Set<Long> holdout = foldsByMemberId.get(signals.memberId()).get(foldIndex);
						results.add(measureCapped(signals, holdout, byPopularity, runner, candidate));
					}
					EvalMetrics.Summary summary = EvalMetrics.summarize(results, candidateCount, albumCandidateCount);
					measurements.add(new EvalMetrics.Measurement(seed, foldIndex, summary));
				}
			}
			return new EvalMetrics.FoldedRun(kind, foldCount, RANDOM_SEEDS, measurements);
		}

		private EvalMetrics.MemberEvalResult measureCapped(EvalSignals signals, Set<Long> holdout,
				List<Long> byPopularity, EvalRunner runner, CapCandidate candidate) {
			if (holdout.isEmpty()) {
				return EvalMetrics.MemberEvalResult.empty(signals.memberId());
			}
			EvalSignals foldSignals = signals.without(holdout);
			// 시뮬레이션 입력 pool 은 캡 없는 자연 순위여야 한다 — 여기서 이미 캡이 걸리면 로컬 시뮬레이션이
			// 캡을 두 번 적용하는 셈이 된다.
			EvalRunner.Result poolResult = runner.recommend(foldSignals, POOL_SIZE, RecommendWeights.DEFAULT,
					RecommendRanker.CapPolicy.UNCAPPED);
			List<RecommendRanker.RankedCandidate> capped = capTopK(poolResult.ranked(), candidate, TOP_K);
			List<EvalMetrics.RecommendedItem> recommended = capped.stream()
					.map(ranked -> toRecommendedItem(ranked.feature(), signals.taste()))
					.toList();

			List<Long> popularityPicks = popularityTopK(foldSignals, byPopularity);
			int popularityHitCount = (int)popularityPicks.stream().filter(holdout::contains).count();
			return new EvalMetrics.MemberEvalResult(signals.memberId(), holdout, recommended, poolResult.fallback(),
					popularityHitCount);
		}

		/**
		 * 가중치·캡 정책을 모두 명시해 측정한다. 바깥 클래스의 {@code measureFolded} 는 {@code EvalRunner} 기본
		 * 캡 정책(HOME)에 기대므로, "캡 도입 전" 처럼 정책을 명시적으로 고정해야 하는 이 판정 테스트에서는
		 * 재사용하지 않는다.
		 */
		private EvalMetrics.FoldedRun measureFoldedExplicit(HoldoutKind kind, int foldCount,
				Map<Long, Map<Long, List<Set<Long>>>> foldsBySeed, Map<Long, List<CoPurchaseIndex>> indexBySeed,
				List<EvalSignals> seedMembers, List<Long> byPopularity, Map<Long, ProductFeature> features,
				long candidateCount, long albumCandidateCount, RecommendWeights weights,
				RecommendRanker.CapPolicy capPolicy) {
			List<EvalMetrics.Measurement> measurements = new ArrayList<>();
			for (Long seed : RANDOM_SEEDS) {
				Map<Long, List<Set<Long>>> foldsByMemberId = foldsBySeed.get(seed);
				List<CoPurchaseIndex> indexesByFold = indexBySeed.get(seed);
				for (int foldIndex = 0; foldIndex < foldCount; foldIndex++) {
					EvalRunner runner = new EvalRunner(recommendRanker, indexesByFold.get(foldIndex), features);
					List<EvalMetrics.MemberEvalResult> results = new ArrayList<>();
					for (EvalSignals signals : seedMembers) {
						Set<Long> holdout = foldsByMemberId.get(signals.memberId()).get(foldIndex);
						results.add(measureExplicit(signals, holdout, byPopularity, runner, weights, capPolicy));
					}
					EvalMetrics.Summary summary = EvalMetrics.summarize(results, candidateCount, albumCandidateCount);
					measurements.add(new EvalMetrics.Measurement(seed, foldIndex, summary));
				}
			}
			return new EvalMetrics.FoldedRun(kind, foldCount, RANDOM_SEEDS, measurements);
		}

		private EvalMetrics.MemberEvalResult measureExplicit(EvalSignals signals, Set<Long> holdout,
				List<Long> byPopularity, EvalRunner runner, RecommendWeights weights,
				RecommendRanker.CapPolicy capPolicy) {
			if (holdout.isEmpty()) {
				return EvalMetrics.MemberEvalResult.empty(signals.memberId());
			}
			EvalSignals foldSignals = signals.without(holdout);
			EvalRunner.Result result = runner.recommend(foldSignals, TOP_K, weights, capPolicy);
			List<EvalMetrics.RecommendedItem> recommended = result.ranked().stream()
					.map(ranked -> toRecommendedItem(ranked.feature(), signals.taste()))
					.toList();

			List<Long> popularityPicks = popularityTopK(foldSignals, byPopularity);
			int popularityHitCount = (int)popularityPicks.stream().filter(holdout::contains).count();
			return new EvalMetrics.MemberEvalResult(signals.memberId(), holdout, recommended, result.fallback(),
					popularityHitCount);
		}

		private double meanMaxArtistShare(EvalMetrics.FoldedRun run) {
			return run.measurements().stream()
					.mapToDouble(measurement -> measurement.summary().diversity().maxArtistShare())
					.average().orElse(0);
		}

		private double meanDistinctLabels(EvalMetrics.FoldedRun run) {
			return run.measurements().stream()
					.mapToDouble(measurement -> measurement.summary().diversity().distinctLabelsRatio())
					.average().orElse(0);
		}

		private double meanCoverage(EvalMetrics.FoldedRun run) {
			return run.measurements().stream()
					.mapToDouble(measurement -> measurement.summary().productCoverage())
					.average().orElse(0);
		}

		private String renderSimulationVsReal(EvalMetrics.FoldedRun wishSimulated, EvalMetrics.FoldedRun wishReal,
				EvalMetrics.FoldedRun purchaseSimulated, EvalMetrics.FoldedRun purchaseReal) {
			StringBuilder builder = new StringBuilder();
			builder.append("\n## 채택 값(maxPerArtist=2, maxPerLabel=3) — 시뮬레이션 vs 실제 CapPolicy.HOME 구현\n\n");
			builder.append("| 홀드아웃 | 구성 | recall@10 mean | maxArtistShare | distinctLabels/10 | coverage@10 "
					+ "| 추천10개미만 |\n|---|---|---|---|---|---|---|\n");
			appendSimulationRow(builder, "위시", "시뮬레이션", wishSimulated);
			appendSimulationRow(builder, "위시", "실제구현", wishReal);
			appendSimulationRow(builder, "구매", "시뮬레이션", purchaseSimulated);
			appendSimulationRow(builder, "구매", "실제구현", purchaseReal);
			return builder.toString();
		}

		private void appendSimulationRow(StringBuilder builder, String holdoutLabel, String phase,
				EvalMetrics.FoldedRun run) {
			builder.append("| ").append(holdoutLabel).append(" | ").append(phase).append(" | ")
					.append(run.recallStats().mean()).append(" | ").append(meanMaxArtistShare(run)).append(" | ")
					.append(meanDistinctLabels(run)).append(" | ").append(meanCoverage(run)).append(" | ")
					.append(run.totalShortRecommendationOccurrences()).append(" |\n");
		}

		private String renderCapImpact(CapImpactStats stats) {
			StringBuilder builder = new StringBuilder();
			builder.append("## 0단계 — 캡 값별 적중 건수·점유 분포 (평가 대상 ").append(stats.evaluatedMembers())
					.append("명)\n\n");
			builder.append("| 캡 값 k | 아티스트 캡 적중(합산) | 레이블 캡 적중(합산) |\n|---|---|---|\n");
			for (int k : List.of(2, 3, 4)) {
				builder.append("| ").append(k).append(" | ").append(stats.artistHitsByK().getOrDefault(k, 0L))
						.append(" | ").append(stats.labelHitsByK().getOrDefault(k, 0L)).append(" |\n");
			}
			builder.append("\n| 점유(top10 내 최대 동일 개수) | 아티스트 회원 수 | 레이블 회원 수 |\n|---|---|---|\n");
			for (int bucket = 1; bucket <= 4; bucket++) {
				builder.append("| ").append(bucket).append(bucket == 4 ? "+" : "").append(" | ")
						.append(stats.artistOccupancy().getOrDefault(bucket, 0)).append(" | ")
						.append(stats.labelOccupancy().getOrDefault(bucket, 0)).append(" |\n");
			}
			return builder.toString();
		}

		private String renderCandidateComparison(CapCandidate candidate, EvalMetrics.FoldedRun wishBaseline,
				EvalMetrics.FoldedRun wishCapped, EvalMetrics.MeasurementStats wishDelta,
				EvalMetrics.FoldedRun purchaseBaseline, EvalMetrics.FoldedRun purchaseCapped,
				EvalMetrics.MeasurementStats purchaseDelta) {
			StringBuilder builder = new StringBuilder();
			builder.append("\n## 캡 후보 maxPerArtist=").append(candidate.maxPerArtist()).append(", maxPerLabel=")
					.append(candidate.maxPerLabel()).append("\n\n");
			builder.append("| 홀드아웃 | recall paired Δ mean | σ | mean-2σ |\n|---|---|---|---|\n");
			builder.append("| 위시 | ").append(wishDelta.mean()).append(" | ").append(wishDelta.stdDev())
					.append(" | ").append(wishDelta.lowerBound()).append(" |\n");
			builder.append("| 구매 | ").append(purchaseDelta.mean()).append(" | ").append(purchaseDelta.stdDev())
					.append(" | ").append(purchaseDelta.lowerBound()).append(" |\n\n");
			builder.append("| 홀드아웃 | 구성 | maxArtistShare | distinctLabels/10 | coverage@10 | 추천10개미만 |\n")
					.append("|---|---|---|---|---|---|\n");
			appendDiversityRow(builder, "위시", "before", wishBaseline);
			appendDiversityRow(builder, "위시", "after", wishCapped);
			appendDiversityRow(builder, "구매", "before", purchaseBaseline);
			appendDiversityRow(builder, "구매", "after", purchaseCapped);
			return builder.toString();
		}

		private void appendDiversityRow(StringBuilder builder, String holdoutLabel, String phase,
				EvalMetrics.FoldedRun run) {
			builder.append("| ").append(holdoutLabel).append(" | ").append(phase).append(" | ")
					.append(meanMaxArtistShare(run)).append(" | ").append(meanDistinctLabels(run)).append(" | ")
					.append(meanCoverage(run)).append(" | ").append(run.totalShortRecommendationOccurrences())
					.append(" |\n");
		}

		/** 캡 값별 적중 건수·점유 분포 측정 결과. */
		private record CapImpactStats(int evaluatedMembers, Map<Integer, Long> artistHitsByK,
				Map<Integer, Long> labelHitsByK, Map<Integer, Integer> artistOccupancy,
				Map<Integer, Integer> labelOccupancy) {
		}

		/** 캡 후보 하나(아티스트 상한, 레이블 상한). */
		private record CapCandidate(int maxPerArtist, int maxPerLabel) {
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
