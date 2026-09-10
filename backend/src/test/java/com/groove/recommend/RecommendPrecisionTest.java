package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.groove.product.entity.Genre;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.GenreRepository;
import com.groove.recommend.dto.RecommendItemResponse;
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
import com.groove.recommend.service.RecommendService;
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
 * 규칙 기반 추천의 품질 측정. 시드 회원의 위시·구매 신호에서 {@link HoldoutSplitter} 로 폴드를 떼고, 남은
 * 신호만으로 계산한 추천 상위 10개에 홀드아웃이 몇 개나 들어오는지 센다. 폴드 하나가 아니라 폴드 × 시드 조합
 * 여러 번을 돌려 평균과 표준편차를 낸다 — 단일 측정값은 개선인지 노이즈인지 구분할 수 없기 때문이다. 신호는
 * DB 에서 한 번만 읽어 메모리에 올리고, 홀드아웃은 행을 지우는 대신 시드 목록에서 뺀 채로
 * {@link RecommendRanker} 를 직접 불러 계산한다({@link EvalRunner}). 일반 빌드에서는 제외되고
 * {@code ./gradlew precisionAt10} 으로만 돈다. 합성 시드 기준이라 실사용 지표가 아니라 규칙이 무작위보다
 * 낫다는 것을 보이는 용도다.
 */
@Tag("precision")
@ActiveProfiles({"local", "seed", "test"})
class RecommendPrecisionTest extends IntegrationTestSupport {

	private static final int TOP_K = 10;
	private static final int HOLDOUT_EVERY = 5;
	private static final int WISH_FOLD_COUNT = 5;
	private static final int PURCHASE_FOLD_COUNT = 3;
	// 회원당 주문 상품이 2~12개로 적어 위시보다 폴드를 적게 잡는다.
	private static final List<Long> RANDOM_SEEDS = List.of(1L, 2L, 3L, 4L, 5L);
	private static final Path LEGACY_REPORT_PATH = Path.of("build", "reports", "precision-at-10.md");
	private static final Path REPORT_PATH = Path.of("build", "reports", "recommend-eval", "home-recall.md");
	private static final Path FOLD_REPORT_PATH = Path.of("build", "reports", "recommend-eval", "fold-stability.md");

	@Autowired
	RecommendService recommendService;

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
	BoughtTogetherRedisService boughtTogetherRedisService;

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

	@Autowired
	GenreRepository genreRepository;

	@Nested
	@DisplayName("recommendHome()")
	class RecommendHome {

		@Test
		@Transactional
		@DisplayName("위시·구매 홀드아웃을 폴드 × 시드로 반복 측정하면 평균에서 2σ 를 빼도 기준선보다 높다")
		void recallAtTenIsStableAcrossFoldsAndSeeds() throws IOException {
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

			// when: 기존 단일 폴드(HOLDOUT_EVERY=5) 재현. 비교 불가능한 단일 측정값이라 참고선으로만 남긴다.
			EvalMetrics.Summary legacySummary = measureLegacy(seedMembers, byPopularity, features, baskets,
					candidateCount, albumCandidateCount);

			List<EvalMetrics.GenreDf> genreDf = EvalMetrics.genreDocumentFrequency(features, genreNames());
			long soldQuantityPositiveCount = countProductsWithSales();
			String fullReport = EvalReport.renderFull(legacySummary, genreDf, soldQuantityPositiveCount);
			System.out.println(fullReport);
			EvalReport.write(LEGACY_REPORT_PATH, EvalReport.renderLegacy(legacySummary));
			EvalReport.write(REPORT_PATH, fullReport);

			// when: 위시/구매 홀드아웃을 폴드 × 시드로 반복 측정해 표준편차를 낸다
			EvalMetrics.FoldedRun wishRun = measureFolded(HoldoutKind.WISH, WISH_FOLD_COUNT, seedMembers,
					byPopularity, features, baskets, candidateCount, albumCandidateCount);
			EvalMetrics.FoldedRun purchaseRun = measureFolded(HoldoutKind.PURCHASE, PURCHASE_FOLD_COUNT,
					seedMembers, byPopularity, features, baskets, candidateCount, albumCandidateCount);

			// then
			String foldReport = EvalReport.renderFolded(legacySummary, wishRun, purchaseRun);
			System.out.println(foldReport);
			EvalReport.write(FOLD_REPORT_PATH, foldReport);

			EvalMetrics.MeasurementStats wishRecall = wishRun.recallStats();
			EvalMetrics.MeasurementStats wishMargin = wishRun.marginOverPopularityStats();
			assertThat(wishRecall.lowerBound())
					.as("위시 홀드아웃 recall@10 mean-2σ 가 무작위 기준선의 3배를 넘어야 한다")
					.isGreaterThan(wishRun.randomBaseline() * 3);
			assertThat(wishMargin.lowerBound())
					.as("규칙 추천이 인기순 대조군을 이기는 폭의 mean-2σ 가 0을 넘어야 한다")
					.isGreaterThan(0);

			EvalMetrics.MeasurementStats purchaseRecall = purchaseRun.recallStats();
			EvalMetrics.MeasurementStats purchaseMargin = purchaseRun.marginOverPopularityStats();
			assertThat(purchaseRecall.lowerBound())
					.as("구매 홀드아웃 recall@10 mean-2σ 가 무작위 기준선의 3배를 넘어야 한다")
					.isGreaterThan(purchaseRun.randomBaseline() * 3);
			assertThat(purchaseMargin.lowerBound())
					.as("구매 홀드아웃에서도 규칙 추천이 인기순 대조군을 이기는 폭의 mean-2σ 가 0을 넘어야 한다")
					.isGreaterThan(0);
		}

		@Test
		@Transactional
		@DisplayName("홀드아웃 없이 계산한 하네스 추천 결과는 recommendHome() 결과와 상품 순서까지 같다")
		void harnessMatchesRecommendHome() {
			// given
			boughtTogetherAggregator.refresh();
			Map<Long, ProductFeature> features = productFeatureCache.get();
			EvalRunner runner = new EvalRunner(recommendRanker, new RedisCoPurchaseIndex(boughtTogetherRedisService),
					features);
			List<EvalSignals> seedMembers = newSignalLoader().loadSeedMembers();
			assertThat(seedMembers).isNotEmpty();

			for (EvalSignals signals : seedMembers) {
				// when
				EvalRunner.Result result = runner.recommend(signals, TOP_K);
				List<Long> productionIds = recommendService.recommendHome(signals.memberId(), TOP_K).items()
						.stream()
						.map(item -> item.product().id())
						.toList();

				// then
				assertThat(result.fallback())
						.as("memberId=%d 는 인기순 폴백을 타면 안 된다", signals.memberId())
						.isFalse();
				assertThat(result.productIds())
						.as("memberId=%d", signals.memberId())
						.containsExactlyElementsOf(productionIds);
			}
		}

		@Test
		@Transactional
		@DisplayName("홀드아웃이 없으면 인메모리 공동구매 집계가 Redis 집계와 같다")
		void inMemoryCoPurchaseIndexMatchesRedisWhenHoldoutIsEmpty() {
			// given
			boughtTogetherAggregator.refresh();
			List<CoPurchaseBasket> baskets = CoPurchaseBasketLoader.load(entityManager, clock);
			Set<Long> allBasketProductIds = baskets.stream()
					.flatMap(basket -> basket.productIds().stream())
					.collect(Collectors.toCollection(LinkedHashSet::new));
			assertThat(allBasketProductIds).isNotEmpty();

			CoPurchaseIndex inMemoryIndex = new InMemoryCoPurchaseIndex(baskets, Map.of());
			CoPurchaseIndex redisIndex = new RedisCoPurchaseIndex(boughtTogetherRedisService);

			// when
			Map<Long, Map<Long, Double>> inMemoryScores = inMemoryIndex.findScores(allBasketProductIds);
			Map<Long, Map<Long, Double>> redisScores = redisIndex.findScores(allBasketProductIds);

			// then
			assertThat(inMemoryScores).isEqualTo(redisScores);
		}
	}

	@Nested
	@DisplayName("recommendRelated()")
	class RecommendRelated {

		@Test
		@Transactional
		@DisplayName("멀티 프레싱 앨범 상품을 기준으로 추천하면 같은 앨범은 빠지고 결과 앨범이 중복되지 않는다")
		void excludesSameAlbumAndDedupsAlbumsAmongCandidates() {
			// given
			Map<Long, Long> albumIdByProductId = new LinkedHashMap<>();
			Map<Long, List<Long>> productIdsByAlbumId = new LinkedHashMap<>();
			for (Object[] row : productIdsWithAlbumIds()) {
				Long productId = (Long)row[0];
				Long albumId = (Long)row[1];
				albumIdByProductId.put(productId, albumId);
				productIdsByAlbumId.computeIfAbsent(albumId, key -> new ArrayList<>()).add(productId);
			}
			Optional<Map.Entry<Long, List<Long>>> multiPressingAlbum = productIdsByAlbumId.entrySet().stream()
					.filter(entry -> entry.getValue().size() >= 2)
					.findFirst();
			assertThat(multiPressingAlbum).isPresent();
			Long targetAlbumId = multiPressingAlbum.get().getKey();
			Long targetProductId = multiPressingAlbum.get().getValue().get(0);

			// when
			List<RecommendItemResponse> items = recommendService.recommendRelated(targetProductId, null, TOP_K);

			// then
			List<Long> resultAlbumIds = items.stream()
					.map(item -> item.product().id())
					.map(albumIdByProductId::get)
					.toList();
			assertThat(resultAlbumIds).doesNotContain(targetAlbumId);
			assertThat(resultAlbumIds).doesNotHaveDuplicates();
		}

		private List<Object[]> productIdsWithAlbumIds() {
			return entityManager
					.createQuery("select p.id, p.album.id from Product p where p.status <> :hidden", Object[].class)
					.setParameter("hidden", ProductStatus.HIDDEN)
					.getResultList();
		}
	}

	/** 기존 단일 폴드(HOLDOUT_EVERY=5) 기준 측정. 재구성 전후 비교용 참고선으로만 쓴다. */
	private EvalMetrics.Summary measureLegacy(List<EvalSignals> seedMembers, List<Long> byPopularity,
			Map<Long, ProductFeature> features, List<CoPurchaseBasket> baskets, long candidateCount,
			long albumCandidateCount) {
		Map<Long, Set<Long>> holdoutByMemberId = new HashMap<>();
		for (EvalSignals signals : seedMembers) {
			holdoutByMemberId.put(signals.memberId(), holdoutOf(signals.wishedIds()));
		}
		EvalRunner runner = new EvalRunner(recommendRanker, new InMemoryCoPurchaseIndex(baskets, holdoutByMemberId),
				features);

		List<EvalMetrics.MemberEvalResult> results = new ArrayList<>();
		for (EvalSignals signals : seedMembers) {
			results.add(measure(signals, holdoutByMemberId.get(signals.memberId()), byPopularity, runner));
		}
		return EvalMetrics.summarize(results, candidateCount, albumCandidateCount);
	}

	/**
	 * kind 를 폴드 수 만큼 등분해 시드마다 전체 폴드를 한 바퀴 돈다. 측정 수는 {@code foldCount * 시드 수}다.
	 * 폴드마다 회원의 상품 특성 스냅샷은 재사용하고 홀드아웃 부분집합만 바뀐다. 공동구매 인덱스도 (kind, seed,
	 * fold) 마다 새로 재집계한다 — 그 폴드의 홀드아웃이 빠진 채로 다시 세야 신호 누수가 없다.
	 */
	private EvalMetrics.FoldedRun measureFolded(HoldoutKind kind, int foldCount, List<EvalSignals> seedMembers,
			List<Long> byPopularity, Map<Long, ProductFeature> features, List<CoPurchaseBasket> baskets,
			long candidateCount, long albumCandidateCount) {
		HoldoutSplitter splitter = new HoldoutSplitter();
		List<EvalMetrics.Measurement> measurements = new ArrayList<>();

		for (Long seed : RANDOM_SEEDS) {
			HoldoutSpec spec = new HoldoutSpec(kind, foldCount, seed);
			Map<Long, List<Set<Long>>> foldsByMemberId = seedMembers.stream()
					.collect(Collectors.toMap(EvalSignals::memberId, signals -> splitter.foldsOf(signals, spec)));

			for (int foldIndex = 0; foldIndex < foldCount; foldIndex++) {
				Map<Long, Set<Long>> holdoutByMemberId = new HashMap<>();
				for (EvalSignals signals : seedMembers) {
					holdoutByMemberId.put(signals.memberId(),
							foldsByMemberId.get(signals.memberId()).get(foldIndex));
				}
				EvalRunner runner = new EvalRunner(recommendRanker,
						new InMemoryCoPurchaseIndex(baskets, holdoutByMemberId), features);

				List<EvalMetrics.MemberEvalResult> results = new ArrayList<>();
				for (EvalSignals signals : seedMembers) {
					Set<Long> holdout = foldsByMemberId.get(signals.memberId()).get(foldIndex);
					results.add(measure(signals, holdout, byPopularity, runner));
				}
				EvalMetrics.Summary summary = EvalMetrics.summarize(results, candidateCount, albumCandidateCount);
				measurements.add(new EvalMetrics.Measurement(seed, foldIndex, summary));
			}
		}

		return new EvalMetrics.FoldedRun(kind, foldCount, RANDOM_SEEDS, measurements);
	}

	private EvalMetrics.MemberEvalResult measure(EvalSignals signals, Set<Long> holdout, List<Long> byPopularity,
			EvalRunner runner) {
		if (holdout.isEmpty()) {
			return EvalMetrics.MemberEvalResult.empty(signals.memberId());
		}

		EvalSignals foldSignals = signals.without(holdout);
		EvalRunner.Result result = runner.recommend(foldSignals, TOP_K);
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

	/**
	 * 대조군. 개인화 없이 평점 높은 순으로 10개를 고른다. 규칙 추천이 균등 무작위보다 낫다는 것만으로는
	 * 부족하고, "그냥 인기순으로 뿌리기"보다 나아야 개인화가 값을 한다고 말할 수 있다. 제외 대상은 홀드아웃을
	 * 뺀 폴드 신호 기준이다 — 홀드아웃 상품도 이 대조군이 맞힐 수 있어야 recall 대조가 공정하다.
	 */
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

	private long countProductsWithSales() {
		return entityManager.createQuery("select count(p) from Product p where p.soldQuantity > 0", Long.class)
				.getSingleResult();
	}

	private Map<Long, String> genreNames() {
		return genreRepository.findAllByOrderByNameAsc().stream()
				.collect(Collectors.toMap(Genre::getId, Genre::getName));
	}

	/** id 오름차순으로 {@value #HOLDOUT_EVERY} 개마다 1개. 난수가 아니라 실행마다 같은 홀드아웃이 나온다. */
	private Set<Long> holdoutOf(List<Long> wishedIds) {
		Set<Long> holdout = new LinkedHashSet<>();
		for (int i = HOLDOUT_EVERY - 1; i < wishedIds.size(); i += HOLDOUT_EVERY) {
			holdout.add(wishedIds.get(i));
		}
		return holdout;
	}

	private EvalSignalLoader newSignalLoader() {
		return new EvalSignalLoader(memberRepository, wishlistRepository, orderItemRepository, recentViewService,
				memberTasteProfileRepository, memberTasteGenreRepository, memberTasteArtistRepository,
				memberTasteDecadeRepository);
	}
}
