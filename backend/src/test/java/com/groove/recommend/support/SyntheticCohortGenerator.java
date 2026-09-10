package com.groove.recommend.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.groove.recommend.entity.Decade;
import com.groove.recommend.service.ProductFeature;
import com.groove.recommend.service.TasteSignal;

/**
 * {@link ProductFeature} 스냅샷만으로 {@link EvalSignals} 와 공동구매 바스켓을 메모리에서 만든다. DB 를
 * 쓰지 않는다 — {@code LocalSignalSeeder} 와 같은 클러스터 70%(노이즈 30%) 구조를 축(장르·아티스트·레이블·
 * 연대)만 바꿔가며 재현해, 스윕이 시드 생성 규칙이 아니라 실제 신호에 반응하는지 구조가 다른 코호트로 확인한다.
 *
 * <p>{@link ClusterAxis#RANDOM} 은 클러스터 후보 집합을 비워 {@link #pickWithNoise} 가 항상 전체 상품에서
 * 뽑게 만든다 — 취향(taste)도 실제 보유 상품과 무관한 표본에서 뽑아 완전히 분리한다. 널 테스트가 이 축을 쓴다.
 */
public final class SyntheticCohortGenerator {

	private static final int WISHLIST_MIN = 10;
	private static final int WISHLIST_MAX = 15;
	private static final int ORDER_MIN = 2;
	private static final int ORDER_MAX = 4;
	private static final int ORDER_ITEM_MAX = 3;
	private static final int MAX_TASTE_ARTISTS = 2;
	private static final int MAX_TASTE_GENRES = 2;
	private static final int MAX_TASTE_DECADES = 2;
	private static final int RANDOM_TASTE_SAMPLE_SIZE = 5;
	private static final long MEMBER_ID_MULTIPLIER = 1_000_000L;

	/** 클러스터 축. MIXED 는 회원마다 GENRE/ARTIST/LABEL/DECADE 를 돌아가며 배정해 코호트 내부를 이질적으로 만든다. */
	public enum ClusterAxis { GENRE, ARTIST, LABEL, DECADE, MIXED, RANDOM }

	/** 회원마다 취향 신호가 몇 % 클러스터에서 나오는지({@code clusterPickPercent}), 회원 수, 난수 시드. */
	public record CohortSpec(ClusterAxis axis, int clusterPickPercent, int memberCount, long randomSeed) {
	}

	/** 생성된 회원 신호와, 그 회원들이 만들었다고 가정하는 주문 바스켓. */
	public record CohortData(List<EvalSignals> signals, List<CoPurchaseBasket> baskets) {
	}

	private static final List<ClusterAxis> MIXED_ROTATION = List.of(ClusterAxis.GENRE, ClusterAxis.ARTIST,
			ClusterAxis.LABEL, ClusterAxis.DECADE);

	public CohortData generate(Map<Long, ProductFeature> features, CohortSpec spec) {
		List<ProductFeature> products = features.values().stream()
				.filter(feature -> !feature.hidden())
				.sorted(Comparator.comparing(ProductFeature::id))
				.toList();
		if (products.isEmpty()) {
			return new CohortData(List.of(), List.of());
		}

		Random random = new Random(spec.randomSeed());
		AtomicLong orderIdCursor = new AtomicLong(1);
		List<EvalSignals> signals = new ArrayList<>();
		List<CoPurchaseBasket> baskets = new ArrayList<>();

		for (int index = 0; index < spec.memberCount(); index++) {
			long memberId = memberIdOf(spec, index);
			ClusterAxis effectiveAxis = spec.axis() == ClusterAxis.MIXED
					? MIXED_ROTATION.get(index % MIXED_ROTATION.size())
					: spec.axis();
			List<ProductFeature> clusterProducts = clusterProductsOf(products, effectiveAxis, random);
			TasteSignal taste = tasteSignalOf(clusterProducts, products, random);

			int wishSize = WISHLIST_MIN + random.nextInt(WISHLIST_MAX - WISHLIST_MIN + 1);
			List<ProductFeature> wished = pickWithNoise(products, clusterProducts, wishSize, Set.of(),
					spec.clusterPickPercent(), random);
			Set<Long> wishedIds = wished.stream().map(ProductFeature::id)
					.collect(Collectors.toCollection(LinkedHashSet::new));

			PurchaseResult purchases = purchasesOf(products, clusterProducts, wishedIds, memberId,
					spec.clusterPickPercent(), random, orderIdCursor);
			baskets.addAll(purchases.baskets());

			signals.add(new EvalSignals(memberId, taste, new ArrayList<>(wishedIds), purchases.purchasedIds(),
					List.of()));
		}
		return new CohortData(signals, baskets);
	}

	/** 시드마다, 코호트마다 겹치지 않는 회원 id. 실제 DB 회원(양수)과 절대 섞이지 않도록 음수 공간을 쓴다. */
	private long memberIdOf(CohortSpec spec, int index) {
		return -(spec.randomSeed() * MEMBER_ID_MULTIPLIER + index + 1);
	}

	private List<ProductFeature> clusterProductsOf(List<ProductFeature> products, ClusterAxis axis, Random random) {
		return switch (axis) {
			case GENRE -> {
				List<Long> genreIds = products.stream().flatMap(feature -> feature.genreIds().stream())
						.distinct().toList();
				yield genreIds.isEmpty() ? List.of()
						: filterBy(products, feature -> feature.genreIds().contains(pick(genreIds, random)));
			}
			case ARTIST -> {
				List<Long> artistIds = distinctNonNull(products, ProductFeature::artistId);
				yield artistIds.isEmpty() ? List.of()
						: filterBy(products, feature -> pick(artistIds, random).equals(feature.artistId()));
			}
			case LABEL -> {
				List<Long> labelIds = distinctNonNull(products, ProductFeature::labelId);
				yield labelIds.isEmpty() ? List.of()
						: filterBy(products, feature -> pick(labelIds, random).equals(feature.labelId()));
			}
			case DECADE -> {
				List<Decade> decades = distinctNonNull(products, ProductFeature::decade);
				yield decades.isEmpty() ? List.of()
						: filterBy(products, feature -> pick(decades, random).equals(feature.decade()));
			}
			case RANDOM -> List.of();
			// MIXED 는 generate() 가 이미 하위 축으로 치환해서 호출하므로 이 분기까지 오지 않는다.
			case MIXED -> List.of();
		};
	}

	private <T> List<T> distinctNonNull(List<ProductFeature> products, Function<ProductFeature, T> extractor) {
		return products.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
	}

	private List<ProductFeature> filterBy(List<ProductFeature> products, Predicate<ProductFeature> predicate) {
		return products.stream().filter(predicate).toList();
	}

	private <T> T pick(List<T> candidates, Random random) {
		return candidates.get(random.nextInt(candidates.size()));
	}

	/**
	 * clusterProducts 가 비어 있으면(RANDOM 축) 실제 보유 상품과 무관한 무작위 표본에서 취향을 뽑아 신호를
	 * 완전히 분리한다. 그 외 축은 clusterProducts 에서 뽑아 실제 시더처럼 취향이 소유 상품과 상관관계를 갖는다.
	 */
	private TasteSignal tasteSignalOf(List<ProductFeature> clusterProducts, List<ProductFeature> allProducts,
			Random random) {
		List<ProductFeature> source = clusterProducts.isEmpty() ? sample(allProducts, RANDOM_TASTE_SAMPLE_SIZE,
				random) : clusterProducts;

		Set<Long> artistIds = pickUpTo(distinctNonNull(source, ProductFeature::artistId), MAX_TASTE_ARTISTS, random);
		Set<Long> genreIds = pickUpTo(source.stream().flatMap(feature -> feature.genreIds().stream()).distinct()
				.toList(), MAX_TASTE_GENRES, random);
		Set<Decade> decades = pickUpTo(distinctNonNull(source, ProductFeature::decade), MAX_TASTE_DECADES, random);
		return new TasteSignal(artistIds, genreIds, decades);
	}

	private <T> List<T> sample(List<T> candidates, int size, Random random) {
		List<T> shuffled = new ArrayList<>(candidates);
		Collections.shuffle(shuffled, random);
		return shuffled.subList(0, Math.min(size, shuffled.size()));
	}

	private <T> Set<T> pickUpTo(List<T> candidates, int max, Random random) {
		if (candidates.isEmpty()) {
			return Set.of();
		}
		List<T> shuffled = new ArrayList<>(candidates);
		Collections.shuffle(shuffled, random);
		return new LinkedHashSet<>(shuffled.subList(0, Math.min(max, shuffled.size())));
	}

	/** {@code LocalSignalSeeder.pickWithNoise} 와 같은 규칙: clusterPickPercent% 는 클러스터에서, 나머지는 전체에서. */
	private List<ProductFeature> pickWithNoise(List<ProductFeature> products, List<ProductFeature> clusterProducts,
			int size, Set<Long> excludedIds, int clusterPickPercent, Random random) {
		Set<Long> pickedIds = new LinkedHashSet<>();
		List<ProductFeature> picked = new ArrayList<>();
		int attempts = 0;
		int maxAttempts = size * 20;

		while (picked.size() < size && attempts < maxAttempts) {
			attempts++;
			List<ProductFeature> pool = random.nextInt(100) < clusterPickPercent && !clusterProducts.isEmpty()
					? clusterProducts
					: products;
			ProductFeature candidate = pool.get(random.nextInt(pool.size()));
			if (!excludedIds.contains(candidate.id()) && pickedIds.add(candidate.id())) {
				picked.add(candidate);
			}
		}
		return picked;
	}

	/** {@code LocalSignalSeeder.seedOrders} 와 같은 규칙으로 주문 여러 건을 만들고 바스켓으로 남긴다. */
	private PurchaseResult purchasesOf(List<ProductFeature> products, List<ProductFeature> clusterProducts,
			Set<Long> wishedIds, long memberId, int clusterPickPercent, Random random, AtomicLong orderIdCursor) {
		int orderCount = ORDER_MIN + random.nextInt(ORDER_MAX - ORDER_MIN + 1);
		List<CoPurchaseBasket> baskets = new ArrayList<>();
		Set<Long> purchasedIds = new LinkedHashSet<>();

		for (int i = 0; i < orderCount; i++) {
			int itemCount = 1 + random.nextInt(ORDER_ITEM_MAX);
			List<ProductFeature> items = pickWithNoise(products, clusterProducts, itemCount, wishedIds,
					clusterPickPercent, random);
			if (items.isEmpty()) {
				continue;
			}
			Set<Long> itemIds = items.stream().map(ProductFeature::id)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			baskets.add(new CoPurchaseBasket(orderIdCursor.getAndIncrement(), memberId, itemIds));
			purchasedIds.addAll(itemIds);
		}
		return new PurchaseResult(new ArrayList<>(purchasedIds), baskets);
	}

	private record PurchaseResult(List<Long> purchasedIds, List<CoPurchaseBasket> baskets) {
	}
}
