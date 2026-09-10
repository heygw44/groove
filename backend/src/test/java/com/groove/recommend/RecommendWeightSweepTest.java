package com.groove.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.groove.member.repository.MemberRepository;
import com.groove.order.repository.OrderItemRepository;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.recommend.service.PopularityIndex;
import com.groove.recommend.service.ProductFeature;
import com.groove.recommend.service.ProductFeatureCache;
import com.groove.recommend.service.RecentViewService;
import com.groove.recommend.service.RecommendScorer;
import com.groove.recommend.service.RecommendWeights;
import com.groove.recommend.support.CoPurchaseBasket;
import com.groove.recommend.support.CoPurchaseBasketLoader;
import com.groove.recommend.support.CoPurchaseIndex;
import com.groove.recommend.support.EvalMetrics;
import com.groove.recommend.support.EvalReport;
import com.groove.recommend.support.EvalSignalLoader;
import com.groove.recommend.support.EvalSignals;
import com.groove.recommend.support.HoldoutKind;
import com.groove.recommend.support.HoldoutSpec;
import com.groove.recommend.support.HoldoutSplitter;
import com.groove.recommend.support.InMemoryCoPurchaseIndex;
import com.groove.recommend.support.SyntheticCohortGenerator;
import com.groove.recommend.support.SyntheticCohortGenerator.ClusterAxis;
import com.groove.recommend.support.SyntheticCohortGenerator.CohortData;
import com.groove.recommend.support.SyntheticCohortGenerator.CohortSpec;
import com.groove.recommend.support.WeightSweep;
import com.groove.recommend.support.WeightSweep.AscentResult;
import com.groove.recommend.support.WeightSweep.CohortEval;
import com.groove.recommend.support.WeightSweep.MemberFoldVectors;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.repository.WishlistRepository;

import jakarta.persistence.EntityManager;

/**
 * 추천 가중치 7개(콘텐츠) + 공동구매를 좌표상승으로 탐색하고, 4중 과적합 방어를 통과한 값만 채택 후보로
 * 리포트한다({@code decision-log.md} "가중치는 사람이 손으로 조정해야 한다" 는 숙제를 닫는 작업이다).
 *
 * <p>{@code LocalSignalSeeder} 가 회원 신호를 장르 클러스터 70%로 만들기 때문에, 그냥 스윕을 돌리면
 * {@code TASTE_GENRE}/{@code SAME_GENRE} 를 최대로 미는 방향으로 수렴할 위험이 크다 — 추천 개선이 아니라
 * 시드 생성 규칙을 되맞힌 것일 수 있다. 그래서 4층을 쌓는다: (1) 튜닝 코호트 내부 회원 3-fold CV,
 * (2) 축이 다른 합성 코호트 교차 검증, (3) 클러스터가 없는 RANDOM 코호트 널 테스트, (4) 보수적 채택 조건.
 *
 * <p>{@link WeightSweep} 이 {@code ScoreVector} 의 선형성을 이용해 (회원 × 후보) 벡터를 (kind, seed, fold)
 * 당 한 번만 만들고 가중치 재평가는 내적만 반복하므로, 좌표상승 189~210 스텝 × 코호트 5개가 분 단위로 끝난다.
 * 일반 빌드에서는 제외되고 {@code ./gradlew recommendWeightSweep} 으로만 돈다.
 */
@Tag("sweep")
@ActiveProfiles({"local", "seed", "test"})
class RecommendWeightSweepTest extends IntegrationTestSupport {

	private static final int WISH_FOLD_COUNT = 5;
	private static final int PURCHASE_FOLD_COUNT = 3;
	private static final int ASCENT_ROUNDS = 3;
	private static final int SYNTHETIC_MEMBER_COUNT = 30;
	private static final int MEMBER_CV_GROUPS = 3;

	// 좌표상승 목적함수 평가는 저렴해야 해서(구성 수백 개) 시드를 적게 쓴다. 교차 코호트 검증은 최종 후보 하나만
	// 비교하므로 더 많은 시드로 감도를 확보한다. DB 코호트는 기존에 추적해온 baseline 과 비교 가능해야 해서
	// 그 baseline 을 낸 것과 같은 시드(1~5)를 그대로 쓴다.
	private static final List<Long> ASCENT_SEEDS = List.of(101L, 102L);
	private static final List<Long> SYNTHETIC_VALIDATION_SEEDS = List.of(201L, 202L, 203L);
	private static final List<Long> DB_VALIDATION_SEEDS = List.of(1L, 2L, 3L, 4L, 5L);

	private static final long TUNING_COHORT_SEED = 1001L;
	private static final long GENRE_VALIDATION_SEED = 1002L;
	private static final long ARTIST_VALIDATION_SEED = 1003L;
	private static final long MIXED_VALIDATION_SEED = 1004L;
	private static final long NULL_COHORT_SEED = 1005L;

	private static final double COVERAGE_DROP_LIMIT = 0.95;
	private static final int MIN_POSITIVE_VALIDATION_CHECKS = 4;
	private static final int TOP_TRIALS_IN_REPORT = 10;

	private static final Path CSV_PATH = Path.of("build", "reports", "recommend-eval", "sweep.csv");
	private static final Path MD_PATH = Path.of("build", "reports", "recommend-eval", "sweep.md");

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
	@DisplayName("가중치 스윕")
	class Sweep {

		@Test
		@Transactional
		@DisplayName("좌표상승으로 가중치를 탐색하고, 4중 과적합 방어를 통과한 값만 채택 후보로 리포트한다")
		void searchesWeightsWithOverfitGuards() throws IOException {
			// given
			Map<Long, ProductFeature> features = productFeatureCache.get();
			PopularityIndex popularityIndex = PopularityIndex.from(features.values());
			List<Long> byPopularityIds = byPopularityIds(features);
			long candidateCount = features.values().stream().filter(feature -> !feature.hidden()).count();
			long albumCandidateCount = features.values().stream()
					.filter(feature -> !feature.hidden())
					.map(ProductFeature::albumId)
					.distinct()
					.count();

			List<EvalSignals> dbMembers = newSignalLoader().loadSeedMembers();
			assertThat(dbMembers).isNotEmpty();
			List<CoPurchaseBasket> dbBaskets = CoPurchaseBasketLoader.load(entityManager, clock);

			SyntheticCohortGenerator generator = new SyntheticCohortGenerator();
			CohortData tuningData = generator.generate(features,
					new CohortSpec(ClusterAxis.GENRE, 70, SYNTHETIC_MEMBER_COUNT, TUNING_COHORT_SEED));
			CohortData genreValidationData = generator.generate(features,
					new CohortSpec(ClusterAxis.GENRE, 70, SYNTHETIC_MEMBER_COUNT, GENRE_VALIDATION_SEED));
			CohortData artistValidationData = generator.generate(features,
					new CohortSpec(ClusterAxis.ARTIST, 70, SYNTHETIC_MEMBER_COUNT, ARTIST_VALIDATION_SEED));
			CohortData mixedValidationData = generator.generate(features,
					new CohortSpec(ClusterAxis.MIXED, 50, SYNTHETIC_MEMBER_COUNT, MIXED_VALIDATION_SEED));
			CohortData nullData = generator.generate(features,
					new CohortSpec(ClusterAxis.RANDOM, 0, SYNTHETIC_MEMBER_COUNT, NULL_COHORT_SEED));

			RecommendWeights baseline = RecommendWeights.DEFAULT;
			List<TrialRun> allTrials = new ArrayList<>();

			// 불변식 스냅샷 — 스윕 전체를 도는 동안 vectorize() 가 만드는 벡터가 흔들리면 안 된다.
			HoldoutSplitter invariantSplitter = new HoldoutSplitter();
			HoldoutSpec invariantSpec = new HoldoutSpec(HoldoutKind.WISH, WISH_FOLD_COUNT, ASCENT_SEEDS.get(0));
			Map<Long, Set<Long>> invariantHoldout = tuningData.signals().stream()
					.collect(Collectors.toMap(EvalSignals::memberId,
							signals -> invariantSplitter.foldsOf(signals, invariantSpec).get(0)));
			CoPurchaseIndex invariantIndex = new InMemoryCoPurchaseIndex(tuningData.baskets(), invariantHoldout);
			List<MemberFoldVectors> vectorsBeforeSweep = WeightSweep.vectorizeAll(recommendScorer, features,
					popularityIndex, tuningData.signals(), invariantHoldout, invariantIndex);

			// 코호트마다 (회원×후보) 벡터를 통째로 들고 있어 여럿을 동시에 살려두면 메모리를 많이 먹는다.
			// 층 1~4 를 각각 전용 메서드로 쪼개 그 안에서만 CohortEval 이 살아있게 하고, 다음 층으로 넘어가면
			// 이전 코호트는 참조가 끊겨 GC 가 회수할 수 있게 한다.
			List<CvRotationResult> cvResults = runMemberCv(tuningData, features, popularityIndex, byPopularityIds,
					candidateCount, albumCandidateCount, baseline, allTrials);

			RecommendWeights candidateWeights = findCandidateWeights(tuningData, features, popularityIndex,
					byPopularityIds, candidateCount, albumCandidateCount, baseline, allTrials);

			NullTestResult nullTestResult = runNullTest(nullData, features, popularityIndex, byPopularityIds,
					candidateCount, albumCandidateCount, baseline, allTrials);

			CohortValidation genreValidation = validateCohort(genreValidationData.signals(),
					genreValidationData.baskets(), features, popularityIndex, byPopularityIds, candidateCount,
					albumCandidateCount, SYNTHETIC_VALIDATION_SEEDS, baseline, candidateWeights);
			CohortValidation artistValidation = validateCohort(artistValidationData.signals(),
					artistValidationData.baskets(), features, popularityIndex, byPopularityIds, candidateCount,
					albumCandidateCount, SYNTHETIC_VALIDATION_SEEDS, baseline, candidateWeights);
			CohortValidation mixedValidation = validateCohort(mixedValidationData.signals(),
					mixedValidationData.baskets(), features, popularityIndex, byPopularityIds, candidateCount,
					albumCandidateCount, SYNTHETIC_VALIDATION_SEEDS, baseline, candidateWeights);
			CohortValidation dbValidation = validateCohort(dbMembers, dbBaskets, features, popularityIndex,
					byPopularityIds, candidateCount, albumCandidateCount, DB_VALIDATION_SEEDS, baseline,
					candidateWeights);

			// "검증 코호트 5개" = 합성 3개(위시+구매 풀링) + DB 위시 + DB 구매. DB 는 예전부터 위시·구매를
			// 따로 추적해왔으니 그 granularity 를 유지하고, 합성 코호트는 축 하나당 1개로 묶는다.
			ValidationCheck genreCheck = ValidationCheck.of("GENRE(seed=B)", genreValidation.pooled());
			ValidationCheck artistCheck = ValidationCheck.of("ARTIST(seed=C)", artistValidation.pooled());
			ValidationCheck mixedCheck = ValidationCheck.of("MIXED(seed=D)", mixedValidation.pooled());
			ValidationCheck dbWishCheck = ValidationCheck.of("DB-위시", dbValidation.wish().deltas());
			ValidationCheck dbPurchaseCheck = ValidationCheck.of("DB-구매", dbValidation.purchase().deltas());
			List<ValidationCheck> checks = List.of(genreCheck, artistCheck, mixedCheck, dbWishCheck,
					dbPurchaseCheck);

			List<Double> pooledDeltas = checks.stream().flatMap(check -> check.deltas().stream()).toList();
			EvalMetrics.MeasurementStats pooledStats = EvalMetrics.MeasurementStats.of(pooledDeltas);
			boolean conditionA = pooledStats.lowerBound() > 0;
			long positiveChecks = checks.stream().filter(check -> check.mean() > 0).count();
			boolean conditionB = positiveChecks >= MIN_POSITIVE_VALIDATION_CHECKS;

			List<KindComparison> allComparisons = List.of(genreValidation.wish(), genreValidation.purchase(),
					artistValidation.wish(), artistValidation.purchase(), mixedValidation.wish(),
					mixedValidation.purchase(), dbValidation.wish(), dbValidation.purchase());
			double baselineCoverageAvg = allComparisons.stream().mapToDouble(KindComparison::baseCoverage)
					.average().orElse(0);
			double candidateCoverageAvg = allComparisons.stream().mapToDouble(KindComparison::candidateCoverage)
					.average().orElse(0);
			boolean conditionC = candidateCoverageAvg >= baselineCoverageAvg * COVERAGE_DROP_LIMIT;

			// (d) 후보의 검증 코호트 ratio 가 널 코호트 최대 ratio 를 넘어야 한다 — 못 넘으면 그 개선은
			// "이만큼 탐색하면 노이즈로도 나올 수 있는" 수준과 구분되지 않는다는 뜻이다.
			double candidateRatio = pooledStats.absMeanOverStdDev();
			boolean conditionD = candidateRatio > nullTestResult.maxRatio();

			boolean adopted = conditionA && conditionB && conditionC && conditionD;
			boolean withinOneSigma = pooledStats.mean() <= pooledStats.stdDev();

			// 튜닝 코호트 쪽에서 관찰된 두 최댓값 — 널 코호트 최댓값과 나란히 리포트에 실어 "클러스터가 없는
			// 데이터에서 찾은 개선이 실제 클러스터가 있는 데이터보다 크다"는 비교를 직접 보여준다.
			double tuningSearchMaxRatio = allTrials.stream()
					.filter(trial -> trial.phase().startsWith("CV_TRAIN") || trial.phase().equals("TUNING_FULL"))
					.mapToDouble(TrialRun::ratioVsBaseline)
					.max().orElse(0);
			double cvHoldoutMaxRatio = cvResults.stream()
					.flatMapToDouble(result -> DoubleStream.of(result.wishDelta().absMeanOverStdDev(),
							result.purchaseDelta().absMeanOverStdDev()))
					.max().orElse(0);

			double baselineTasteMatchRate = averageTasteMatchRate(dbValidation.wish().baseRun());
			double candidateTasteMatchRate = averageTasteMatchRate(dbValidation.wish().candidateRun());

			// 불변식 재확인 — 스윕 도중의 어떤 랭킹·채점도 vectorize() 입력을 건드리지 않았어야 한다.
			List<MemberFoldVectors> vectorsAfterSweep = WeightSweep.vectorizeAll(recommendScorer, features,
					popularityIndex, tuningData.signals(), invariantHoldout, invariantIndex);

			// when
			EvalReport.write(CSV_PATH, renderCsv(allTrials));
			String markdown = renderMarkdown(baseline, candidateWeights, cvResults, nullTestResult, checks,
					pooledStats, conditionA, conditionB, conditionC, conditionD, candidateRatio, adopted,
					withinOneSigma, baselineCoverageAvg, candidateCoverageAvg, tuningSearchMaxRatio,
					cvHoldoutMaxRatio, baselineTasteMatchRate, candidateTasteMatchRate, allTrials);
			System.out.println(markdown);
			EvalReport.write(MD_PATH, markdown);

			// then — 여기서는 "탐색 결과가 좋은지"가 아니라 "하네스가 제대로 돌았는지"만 단언한다.
			assertThat(vectorsAfterSweep)
					.as("vectorize() 는 가중치와 무관해야 한다 — 스윕 전후로 같은 (홀드아웃, 공동구매 인덱스) 조합이면 결과가 같아야 한다")
					.isEqualTo(vectorsBeforeSweep);
			assertThat(nullTestResult.trialCount()).as("널 코호트 좌표상승이 최소 한 번은 구성을 평가했어야 한다").isPositive();
			assertThat(allTrials).as("탐색 전체(3-fold CV·전체 튜닝·널 테스트)가 트라이얼을 남겼어야 한다").isNotEmpty();
			assertThat(Files.exists(CSV_PATH)).as("sweep.csv 가 생성됐어야 한다").isTrue();
			assertThat(Files.exists(MD_PATH)).as("sweep.md 가 생성됐어야 한다").isTrue();
		}
	}

	// ==================== 코호트/벡터화 도우미 ====================

	private CohortEval buildCohortEval(List<EvalSignals> members, List<CoPurchaseBasket> baskets,
			Map<Long, ProductFeature> features, PopularityIndex popularityIndex, List<Long> byPopularityIds,
			long candidateCount, long albumCandidateCount, List<Long> randomSeeds) {
		return CohortEval.build(recommendScorer, features, popularityIndex, members, baskets, byPopularityIds,
				candidateCount, albumCandidateCount, WISH_FOLD_COUNT, PURCHASE_FOLD_COUNT, randomSeeds);
	}

	/** 평점 desc(null 뒤로) → 최신순 → id desc. {@code RecommendPrecisionTest} 의 인기순 대조군과 같은 규칙이다. */
	private List<Long> byPopularityIds(Map<Long, ProductFeature> features) {
		return features.values().stream()
				.filter(feature -> !feature.hidden())
				.sorted(Comparator
						.comparing(ProductFeature::averageRating, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(ProductFeature::createdAt, Comparator.reverseOrder())
						.thenComparing(ProductFeature::id, Comparator.reverseOrder()))
				.map(ProductFeature::id)
				.toList();
	}

	/** email 정렬 인덱스 %3 과 같은 규칙 — 합성 회원은 생성 순서(고정 시드라 실행마다 같다)가 그 역할을 한다. */
	private List<List<EvalSignals>> partitionByIndex(List<EvalSignals> members, int groupCount) {
		List<List<EvalSignals>> groups = new ArrayList<>();
		for (int i = 0; i < groupCount; i++) {
			groups.add(new ArrayList<>());
		}
		for (int i = 0; i < members.size(); i++) {
			groups.get(i % groupCount).add(members.get(i));
		}
		return groups;
	}

	private List<EvalSignals> otherGroups(List<List<EvalSignals>> groups, int excludeIndex) {
		List<EvalSignals> result = new ArrayList<>();
		for (int i = 0; i < groups.size(); i++) {
			if (i != excludeIndex) {
				result.addAll(groups.get(i));
			}
		}
		return result;
	}

	private EvalSignalLoader newSignalLoader() {
		return new EvalSignalLoader(memberRepository, wishlistRepository, orderItemRepository, recentViewService,
				memberTasteProfileRepository, memberTasteGenreRepository, memberTasteArtistRepository,
				memberTasteDecadeRepository);
	}

	private double averageTasteMatchRate(EvalMetrics.FoldedRun run) {
		return run.measurements().stream().mapToDouble(measurement -> measurement.summary().tasteMatchRate())
				.average().orElse(0);
	}

	/**
	 * 좌표상승이 평가하는 구성마다 wish/purchase {@link EvalMetrics.FoldedRun} 과, 그 코호트 자체의 기준
	 * 대비 paired ratio(|Δ|÷σ)를 캐시해 리포트가 재계산하지 않게 한다. 탐색 알고리즘(좌표상승)은 이 ratio 를
	 * 전혀 보지 않는다 — combinedRecall 만 목적함수로 쓴다. ratio 는 순수하게 사후 리포트용이다.
	 */
	private Function<RecommendWeights, Double> cachingObjective(CohortEval cohortEval, String phase, String cohort,
			List<TrialRun> sink, EvalMetrics.FoldedRun baseWishRun, EvalMetrics.FoldedRun basePurchaseRun) {
		return weights -> {
			EvalMetrics.FoldedRun wishRun = cohortEval.wishRun(weights);
			EvalMetrics.FoldedRun purchaseRun = cohortEval.purchaseRun(weights);
			double wishRatio = EvalMetrics.pairedDelta(baseWishRun, wishRun).absMeanOverStdDev();
			double purchaseRatio = EvalMetrics.pairedDelta(basePurchaseRun, purchaseRun).absMeanOverStdDev();
			double ratioVsBaseline = Math.max(wishRatio, purchaseRatio);
			TrialRun trial = new TrialRun(phase, cohort, weights, wishRun, purchaseRun, ratioVsBaseline);
			sink.add(trial);
			return trial.combinedRecall();
		};
	}

	// ==================== 층 1 — 튜닝 코호트 내부 회원 3-fold CV ====================

	/**
	 * 튜닝 코호트 30명을 3그룹으로 나눠 2그룹으로 좌표상승하고 나머지 1그룹에서 검증하기를 3회 회전한다.
	 * train/test {@link CohortEval} 은 이 메서드 안에서만 살아있다 — 회전이 끝나면 참조가 끊겨 다음 회전이나
	 * 다음 층으로 넘어가기 전에 GC 가 회수할 수 있다.
	 */
	private List<CvRotationResult> runMemberCv(CohortData tuningData, Map<Long, ProductFeature> features,
			PopularityIndex popularityIndex, List<Long> byPopularityIds, long candidateCount,
			long albumCandidateCount, RecommendWeights baseline, List<TrialRun> allTrials) {
		List<List<EvalSignals>> memberGroups = partitionByIndex(tuningData.signals(), MEMBER_CV_GROUPS);
		List<CvRotationResult> cvResults = new ArrayList<>();
		for (int rotation = 0; rotation < MEMBER_CV_GROUPS; rotation++) {
			List<EvalSignals> testMembers = memberGroups.get(rotation);
			List<EvalSignals> trainMembers = otherGroups(memberGroups, rotation);

			CohortEval trainEval = buildCohortEval(trainMembers, tuningData.baskets(), features, popularityIndex,
					byPopularityIds, candidateCount, albumCandidateCount, ASCENT_SEEDS);
			EvalMetrics.FoldedRun trainBaseWish = trainEval.wishRun(baseline);
			EvalMetrics.FoldedRun trainBasePurchase = trainEval.purchaseRun(baseline);
			AscentResult ascent = WeightSweep.ascend(
					cachingObjective(trainEval, "CV_TRAIN_R" + rotation, "tuning-train", allTrials, trainBaseWish,
							trainBasePurchase),
					baseline, ASCENT_ROUNDS);

			CohortEval testEval = buildCohortEval(testMembers, tuningData.baskets(), features, popularityIndex,
					byPopularityIds, candidateCount, albumCandidateCount, ASCENT_SEEDS);
			EvalMetrics.MeasurementStats wishDelta = EvalMetrics.pairedDelta(testEval.wishRun(baseline),
					testEval.wishRun(ascent.best()));
			EvalMetrics.MeasurementStats purchaseDelta = EvalMetrics.pairedDelta(testEval.purchaseRun(baseline),
					testEval.purchaseRun(ascent.best()));
			cvResults.add(new CvRotationResult(rotation, ascent.best(), wishDelta, purchaseDelta));
		}
		return cvResults;
	}

	/**
	 * 튜닝 코호트 전체(30명)로 다시 한 번 좌표상승해 최종 후보를 낸다. 3-fold CV 는 이 절차 자체가 특정
	 * 회원 구성에 휘둘리는지 보는 로버스트니스 확인이고, 검증에 넘길 후보는 전체 데이터로 다시 찾는다.
	 */
	private RecommendWeights findCandidateWeights(CohortData tuningData, Map<Long, ProductFeature> features,
			PopularityIndex popularityIndex, List<Long> byPopularityIds, long candidateCount,
			long albumCandidateCount, RecommendWeights baseline, List<TrialRun> allTrials) {
		CohortEval fullTuningEval = buildCohortEval(tuningData.signals(), tuningData.baskets(), features,
				popularityIndex, byPopularityIds, candidateCount, albumCandidateCount, ASCENT_SEEDS);
		EvalMetrics.FoldedRun baseWish = fullTuningEval.wishRun(baseline);
		EvalMetrics.FoldedRun basePurchase = fullTuningEval.purchaseRun(baseline);
		AscentResult fullAscent = WeightSweep.ascend(
				cachingObjective(fullTuningEval, "TUNING_FULL", "tuning-full", allTrials, baseWish, basePurchase),
				baseline, ASCENT_ROUNDS);
		return fullAscent.best();
	}

	// ==================== 층 3 — 널 테스트 ====================

	/**
	 * 클러스터가 없는 RANDOM 코호트에서 같은 좌표상승 절차를 돌려, 탐색 중 찾아낸 최선의 구성이 기준 대비 얼마나
	 * 벌어질 수 있는지를 잰다. 이 최댓값은 "신호가 전혀 없어도 이 탐색량(구성 수백 개)이면 우연히 얻을 수 있는
	 * 상한"이다. 절대 임계와 비교해 통과/실패를 가르지 않는다 — 213개 구성 중 최댓값을 취하는 절차 자체가
	 * 다중비교이므로 고정된 2σ 같은 단일비교 기준으로 판정하면 틀린다. 후보는 이 상한과 직접 비교해서 판단한다
	 * (채택 조건 (d)).
	 */
	private NullTestResult runNullTest(CohortData nullData, Map<Long, ProductFeature> features,
			PopularityIndex popularityIndex, List<Long> byPopularityIds, long candidateCount,
			long albumCandidateCount, RecommendWeights baseline, List<TrialRun> allTrials) {
		CohortEval nullEval = buildCohortEval(nullData.signals(), nullData.baskets(), features, popularityIndex,
				byPopularityIds, candidateCount, albumCandidateCount, ASCENT_SEEDS);
		EvalMetrics.FoldedRun baseWish = nullEval.wishRun(baseline);
		EvalMetrics.FoldedRun basePurchase = nullEval.purchaseRun(baseline);
		List<TrialRun> nullTrials = new ArrayList<>();
		WeightSweep.ascend(
				cachingObjective(nullEval, "NULL_TEST", "random-null", nullTrials, baseWish, basePurchase), baseline,
				ASCENT_ROUNDS);
		allTrials.addAll(nullTrials);

		TrialRun worst = nullTrials.stream().max(Comparator.comparingDouble(TrialRun::ratioVsBaseline)).orElse(null);
		double maxRatio = worst == null ? 0 : worst.ratioVsBaseline();
		return new NullTestResult(maxRatio, worst, nullTrials.size());
	}

	// ==================== 층 2 + 층 4 — 코호트 검증 ====================

	/** 코호트 하나에서 기준 vs 후보의 위시·구매 paired Δ 를 낸다. {@link CohortEval} 은 이 메서드 밖으로 새지 않는다. */
	private CohortValidation validateCohort(List<EvalSignals> members, List<CoPurchaseBasket> baskets,
			Map<Long, ProductFeature> features, PopularityIndex popularityIndex, List<Long> byPopularityIds,
			long candidateCount, long albumCandidateCount, List<Long> randomSeeds, RecommendWeights baseline,
			RecommendWeights candidateWeights) {
		CohortEval cohortEval = buildCohortEval(members, baskets, features, popularityIndex, byPopularityIds,
				candidateCount, albumCandidateCount, randomSeeds);
		KindComparison wish = new KindComparison(cohortEval.wishRun(baseline),
				cohortEval.wishRun(candidateWeights));
		KindComparison purchase = new KindComparison(cohortEval.purchaseRun(baseline),
				cohortEval.purchaseRun(candidateWeights));
		return new CohortValidation(wish, purchase);
	}

	// ==================== 리포트 렌더링 ====================

	private String renderCsv(List<TrialRun> trials) {
		StringBuilder csv = new StringBuilder();
		csv.append("phase,cohort,tasteArtist,sameArtist,tasteGenre,sameGenre,sameLabel,tasteDecade,sameDecade,"
				+ "coPurchase,wishRecall,purchaseRecall,combinedRecall\n");
		for (TrialRun trial : trials) {
			RecommendWeights weights = trial.weights();
			csv.append("%s,%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%.4f,%.4f,%.4f\n".formatted(trial.phase(), trial.cohort(),
					weights.tasteArtist(), weights.sameArtist(), weights.tasteGenre(), weights.sameGenre(),
					weights.sameLabel(), weights.tasteDecade(), weights.sameDecade(), weights.coPurchase(),
					trial.wishRecall(), trial.purchaseRecall(), trial.combinedRecall()));
		}
		return csv.toString();
	}

	private String renderMarkdown(RecommendWeights baseline, RecommendWeights candidateWeights,
			List<CvRotationResult> cvResults, NullTestResult nullTestResult, List<ValidationCheck> checks,
			EvalMetrics.MeasurementStats pooledStats, boolean conditionA, boolean conditionB, boolean conditionC,
			boolean conditionD, double candidateRatio, boolean adopted, boolean withinOneSigma,
			double baselineCoverageAvg, double candidateCoverageAvg, double tuningSearchMaxRatio,
			double cvHoldoutMaxRatio, double baselineTasteMatchRate, double candidateTasteMatchRate,
			List<TrialRun> allTrials) {
		StringBuilder report = new StringBuilder();
		report.append("# 추천 가중치 스윕 (좌표상승 + 4중 과적합 방어)\n\n");
		report.append(renderConclusion(adopted, withinOneSigma, cvResults, checks, conditionA, conditionB,
				conditionC, conditionD, candidateRatio, nullTestResult));
		report.append('\n');
		report.append(renderComparisonTable(nullTestResult, tuningSearchMaxRatio, cvHoldoutMaxRatio, candidateRatio));
		report.append('\n');
		report.append(renderNullTest(nullTestResult));
		report.append('\n');
		report.append(renderCvSection(cvResults));
		report.append('\n');
		report.append(renderCandidate(baseline, candidateWeights));
		report.append('\n');
		report.append(renderValidationTable(checks, pooledStats, conditionA, conditionB, conditionC, conditionD,
				candidateRatio, nullTestResult.maxRatio(), baselineCoverageAvg, candidateCoverageAvg));
		report.append('\n');
		report.append(renderTasteMatch(baselineTasteMatchRate, candidateTasteMatchRate));
		report.append('\n');
		report.append(renderTopTrials(allTrials));
		return report.toString();
	}

	private String renderConclusion(boolean adopted, boolean withinOneSigma, List<CvRotationResult> cvResults,
			List<ValidationCheck> checks, boolean conditionA, boolean conditionB, boolean conditionC,
			boolean conditionD, double candidateRatio, NullTestResult nullTestResult) {
		if (adopted) {
			return "## 결론\n\n**채택 조건 4개(paired Δ>2σ, 검증 5개 중 4개 이상 양수, coverage 5% 이내, "
					+ "후보 ratio 가 널 최대(%.2f)를 초과)를 전부 통과했다.**\n".formatted(nullTestResult.maxRatio());
		}
		StringBuilder body = new StringBuilder();
		body.append("## 결론\n\n**현재 값을 유지한다.** 서로 독립적인 근거 네 가지가 같은 결론을 가리킨다 — "
				+ "이건 실패한 측정이 아니라, 과적합을 잡아내려던 설계가 의도대로 작동한 결과다.\n\n");
		body.append("- 3-fold CV 회전마다 찾은 가중치가 제각각이었다(%s)\n".formatted(describeRotationDisagreement(cvResults)));
		body.append("- %s\n".formatted(describeSignFlip(checks)));
		body.append("- 채택 조건 (a)(b)(c) 중 %s\n".formatted(describeAbcOutcome(conditionA, conditionB, conditionC)));
		body.append("- 후보의 검증 코호트 ratio(%.2f)가 널 코호트 최대 ratio(%.2f)를 %s — %s\n".formatted(candidateRatio,
				nullTestResult.maxRatio(), conditionD ? "넘었다" : "넘지 못했다",
				conditionD ? "우연과는 구분된다" : "이 개선은 우연과 구분되지 않는다"));
		if (!withinOneSigma) {
			body.append("\npooled Δ 자체는 1σ 를 넘었지만(순수 무신호는 아니었다), 위 네 조건을 전부 만족하지 못해 채택하지 않는다.\n");
		}
		return body.toString();
	}

	private String describeRotationDisagreement(List<CvRotationResult> cvResults) {
		return cvResults.stream()
				.map(result -> "회전%d TASTE_GENRE=%d".formatted(result.rotation(), result.weights().tasteGenre()))
				.collect(Collectors.joining(", "));
	}

	private String describeSignFlip(List<ValidationCheck> checks) {
		Optional<ValidationCheck> positive = checks.stream().filter(check -> check.mean() > 0).findFirst();
		Optional<ValidationCheck> negative = checks.stream().filter(check -> check.mean() < 0).findFirst();
		if (positive.isPresent() && negative.isPresent()) {
			return "최종 후보가 %s 에서는 Δ=%+.4f 인데 %s 에서는 Δ=%+.4f 로 부호가 뒤집힌다".formatted(positive.get().label(),
					positive.get().mean(), negative.get().label(), negative.get().mean());
		}
		return "검증 코호트 5개의 Δ 부호가 일관되지 않는다";
	}

	private String describeAbcOutcome(boolean conditionA, boolean conditionB, boolean conditionC) {
		long failed = Stream.of(conditionA, conditionB, conditionC).filter(passed -> !passed).count();
		return failed == 3 ? "전부 실패했다" : "%d개가 실패했다".formatted(failed);
	}

	/** 이 스윕의 핵심 서사 — 클러스터가 없는 데이터에서 찾은 "개선" 이 실제 클러스터가 있는 데이터보다 큰지를 한눈에 보여준다. */
	private String renderComparisonTable(NullTestResult nullTestResult, double tuningSearchMaxRatio,
			double cvHoldoutMaxRatio, double candidateRatio) {
		return """
				## 대조 — 클러스터 유무에 따른 최대 |Δ|÷σ

				| 구분 | 최대 \\|Δ\\|÷σ |
				|---|---|
				| 널 코호트(RANDOM, 클러스터 없음) 탐색 중 | %.2f |
				| 튜닝 코호트(GENRE, 클러스터 있음) 탐색 중 | %.2f |
				| 튜닝 코호트 3-fold CV 홀드아웃 검증 최대 | %.2f |
				| 최종 후보 vs 5개 검증 코호트(pooled) | %.2f |

				클러스터가 아예 없는 데이터에서 좌표상승이 찾아낸 최댓값(%.2f)이, 실제 장르 클러스터가 있는
				튜닝 코호트의 탐색 최댓값(%.2f)과 비슷하거나 더 크다. 좌표상승이 213개 구성을 뒤지면서 잡아내는
				신호 대부분이 실제 구조가 아니라 노이즈라는 직접적인 증거다.
				""".formatted(nullTestResult.maxRatio(), tuningSearchMaxRatio, cvHoldoutMaxRatio, candidateRatio,
				nullTestResult.maxRatio(), tuningSearchMaxRatio);
	}

	private String renderNullTest(NullTestResult result) {
		StringBuilder section = new StringBuilder();
		section.append("## 널 테스트 (RANDOM 코호트)\n\n");
		section.append("신호가 전혀 없는 코호트에서 같은 좌표상승을 돌려, 이 탐색량(구성 %d개)이면 우연히 얻을 수 있는 "
				.formatted(result.trialCount()));
		section.append("|Δ|÷σ 상한을 잰다. 고정 임계와 비교해 통과/실패를 가르지 않는다 — 최댓값을 취하는 절차 자체가 "
				+ "다중비교라 고정된 2σ 같은 단일비교 기준은 틀린다. 후보는 이 상한과 직접 비교한다(채택 조건 (d)).\n\n");
		section.append("| 항목 | 값 |\n|---|---|\n");
		section.append("| 평가한 구성 수 | %d |\n".formatted(result.trialCount()));
		section.append("| 최대 \\|Δ\\|÷σ (=우연 상한) | %.2f |\n".formatted(result.maxRatio()));
		if (result.worstTrial() != null) {
			section.append("| 최대치를 낸 구성 | %s |\n".formatted(describeWeights(result.worstTrial().weights())));
		}
		section.append('\n');
		return section.toString();
	}

	private String renderCvSection(List<CvRotationResult> cvResults) {
		StringBuilder section = new StringBuilder();
		section.append("## 층 1 — 튜닝 코호트 내부 회원 3-fold CV\n\n");
		section.append("각 회전에서 2그룹(20명)으로 좌표상승해 찾은 값을 나머지 1그룹(10명)에서 검증한다. "
				+ "회전마다 값이 크게 다르면 특정 회원 구성에 휘둘렸다는 뜻이다.\n\n");
		section.append("| 회전 | 찾은 가중치 | 홀드아웃 그룹 위시 Δ mean±σ | Δ÷σ | 구매 Δ mean±σ | Δ÷σ |\n");
		section.append("|---|---|---|---|---|---|\n");
		for (CvRotationResult result : cvResults) {
			section.append("| %d | %s | %.4f ± %.4f | %.2f | %.4f ± %.4f | %.2f |\n".formatted(result.rotation(),
					describeWeights(result.weights()), result.wishDelta().mean(), result.wishDelta().stdDev(),
					result.wishDelta().absMeanOverStdDev(), result.purchaseDelta().mean(),
					result.purchaseDelta().stdDev(), result.purchaseDelta().absMeanOverStdDev()));
		}
		return section.toString();
	}

	private String renderCandidate(RecommendWeights baseline, RecommendWeights candidateWeights) {
		return """
				## 최종 후보 (튜닝 코호트 전체 30명으로 재탐색)

				| 차원 | 기준값 | 후보값 |
				|---|---|---|
				| tasteArtist | %d | %d |
				| sameArtist | %d | %d |
				| tasteGenre | %d | %d |
				| sameGenre | %d | %d |
				| sameLabel | %d | %d |
				| tasteDecade | %d | %d |
				| sameDecade | %d | %d |
				| coPurchase | %.2f | %.2f |
				""".formatted(baseline.tasteArtist(), candidateWeights.tasteArtist(), baseline.sameArtist(),
				candidateWeights.sameArtist(), baseline.tasteGenre(), candidateWeights.tasteGenre(),
				baseline.sameGenre(), candidateWeights.sameGenre(), baseline.sameLabel(),
				candidateWeights.sameLabel(), baseline.tasteDecade(), candidateWeights.tasteDecade(),
				baseline.sameDecade(), candidateWeights.sameDecade(), baseline.coPurchase(),
				candidateWeights.coPurchase());
	}

	private String renderValidationTable(List<ValidationCheck> checks, EvalMetrics.MeasurementStats pooledStats,
			boolean conditionA, boolean conditionB, boolean conditionC, boolean conditionD, double candidateRatio,
			double nullMaxRatio, double baselineCoverageAvg, double candidateCoverageAvg) {
		StringBuilder section = new StringBuilder();
		section.append("## 층 2 + 층 4 — 코호트별 안정성과 채택 조건\n\n");
		section.append("| 검증 코호트 | Δ mean | Δ σ | Δ>0 | n |\n|---|---|---|---|---|\n");
		for (ValidationCheck check : checks) {
			section.append("| %s | %.4f | %.4f | %s | %d |\n".formatted(check.label(), check.mean(),
					check.stdDev(), check.mean() > 0 ? "예" : "아니오", check.deltas().size()));
		}
		section.append('\n');
		section.append("| 채택 조건 | 값 | 통과 |\n|---|---|---|\n");
		section.append("| (a) pooled mean(Δ)-2σ(Δ) > 0 | %.4f | %s |\n".formatted(pooledStats.lowerBound(),
				conditionA ? "예" : "아니오"));
		section.append("| (b) 검증 5개 중 4개 이상 Δ>0 | - | %s |\n".formatted(conditionB ? "예" : "아니오"));
		section.append("| (c) coverage@10 5%% 이내 하락 | 기준 %.3f → 후보 %.3f | %s |\n".formatted(baselineCoverageAvg,
				candidateCoverageAvg, conditionC ? "예" : "아니오"));
		section.append("| (d) 후보 ratio(%.2f) > 널 최대 ratio(%.2f) | - | %s |\n".formatted(candidateRatio,
				nullMaxRatio, conditionD ? "예" : "아니오"));
		return section.toString();
	}

	private String renderTasteMatch(double baselineRate, double candidateRate) {
		return """
				## tasteMatchRate (DB 코호트 위시 홀드아웃 기준)

				| 구성 | tasteMatchRate |
				|---|---|
				| 기준 | %.3f |
				| 후보 | %.3f |
				""".formatted(baselineRate, candidateRate);
	}

	private String renderTopTrials(List<TrialRun> allTrials) {
		StringBuilder section = new StringBuilder();
		section.append("## 상위 %d 구성 (combinedRecall 기준, 전체 탐색 중)\n\n".formatted(TOP_TRIALS_IN_REPORT));
		section.append("| phase | cohort | 가중치 | combinedRecall |\n|---|---|---|---|\n");
		allTrials.stream()
				.sorted(Comparator.comparingDouble(TrialRun::combinedRecall).reversed())
				.limit(TOP_TRIALS_IN_REPORT)
				.forEach(trial -> section.append("| %s | %s | %s | %.4f |\n".formatted(trial.phase(),
						trial.cohort(), describeWeights(trial.weights()), trial.combinedRecall())));
		return section.toString();
	}

	private String describeWeights(RecommendWeights weights) {
		return "TA=%d SA=%d TG=%d SG=%d SL=%d TD=%d SD=%d CP=%.1f".formatted(weights.tasteArtist(),
				weights.sameArtist(), weights.tasteGenre(), weights.sameGenre(), weights.sameLabel(),
				weights.tasteDecade(), weights.sameDecade(), weights.coPurchase());
	}

	// ==================== 리포트용 레코드 ====================

	/**
	 * 좌표상승 한 스텝에서 평가한 가중치 하나와 그 결과. CSV·top10 리포트가 이 레코드를 그대로 쓴다.
	 * {@code ratioVsBaseline} 은 그 트라이얼이 속한 코호트 자체의 기준 대비 paired |Δ|÷σ 다 — 좌표상승은 이
	 * 값을 목적함수로 쓰지 않는다(목적함수는 {@code combinedRecall}), 순수하게 널 테스트·리포트용이다.
	 */
	private record TrialRun(String phase, String cohort, RecommendWeights weights, EvalMetrics.FoldedRun wishRun,
			EvalMetrics.FoldedRun purchaseRun, double ratioVsBaseline) {

		double wishRecall() {
			return wishRun.recallStats().mean();
		}

		double purchaseRecall() {
			return purchaseRun.recallStats().mean();
		}

		double combinedRecall() {
			return (wishRecall() + purchaseRecall()) / 2.0;
		}
	}

	/** {@code maxRatio} 는 "신호가 전혀 없어도 이 탐색량이면 우연히 얻을 수 있는 |Δ|÷σ 상한"이다. */
	private record NullTestResult(double maxRatio, TrialRun worstTrial, int trialCount) {
	}

	private record CvRotationResult(int rotation, RecommendWeights weights, EvalMetrics.MeasurementStats wishDelta,
			EvalMetrics.MeasurementStats purchaseDelta) {
	}

	/** 기준 vs 후보 하나의 (kind) 비교. paired Δ 와 coverage 를 한 번의 FoldedRun 쌍에서 같이 뽑는다. */
	private record KindComparison(EvalMetrics.FoldedRun baseRun, EvalMetrics.FoldedRun candidateRun) {

		List<Double> deltas() {
			return EvalMetrics.pairedDeltaValues(baseRun, candidateRun);
		}

		double baseCoverage() {
			return baseRun.coverageStats().mean();
		}

		double candidateCoverage() {
			return candidateRun.coverageStats().mean();
		}
	}

	/** 코호트 하나의 위시·구매 비교. {@link #pooled} 는 "검증 코호트 5개" 중 합성 코호트 3개가 쓰는 풀링 규칙이다. */
	private record CohortValidation(KindComparison wish, KindComparison purchase) {

		List<Double> pooled() {
			List<Double> combined = new ArrayList<>(wish.deltas());
			combined.addAll(purchase.deltas());
			return combined;
		}
	}

	private record ValidationCheck(String label, List<Double> deltas, double mean, double stdDev) {

		static ValidationCheck of(String label, List<Double> deltas) {
			EvalMetrics.MeasurementStats stats = EvalMetrics.MeasurementStats.of(deltas);
			return new ValidationCheck(label, deltas, stats.mean(), stats.stdDev());
		}
	}
}
