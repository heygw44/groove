package com.groove.recommend.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.recommend.dto.HomeRecommendResponse;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.dto.RecommendItemResponse;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.mapper.RecommendQueryMapper;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;
import com.groove.wishlist.repository.WishlistRepository;

import lombok.RequiredArgsConstructor;

/** 홈 추천·상세 관련 상품 추천. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RecommendService {

	static final int HOME_DEFAULT_SIZE = 12;
	static final int HOME_MAX_SIZE = 30;
	static final int RELATED_DEFAULT_SIZE = 8;
	static final int RELATED_MAX_SIZE = 20;

	private static final Comparator<RankedCandidate> RANKING_COMPARATOR = Comparator
			.comparingDouble((RankedCandidate candidate) -> candidate.score().totalScore())
			.reversed()
			.thenComparing(candidate -> candidate.feature().averageRating(),
					Comparator.nullsLast(Comparator.<Double>reverseOrder()))
			.thenComparing(candidate -> candidate.feature().createdAt(), Comparator.reverseOrder())
			.thenComparing(candidate -> candidate.feature().id(), Comparator.reverseOrder());

	private final RecommendQueryMapper recommendQueryMapper;
	private final RecommendScorer recommendScorer;
	private final BoughtTogetherRedisService boughtTogetherRedisService;
	private final RecentViewService recentViewService;
	private final WishlistRepository wishlistRepository;
	private final OrderItemRepository orderItemRepository;
	private final MemberTasteProfileRepository memberTasteProfileRepository;
	private final MemberTasteGenreRepository memberTasteGenreRepository;
	private final MemberTasteArtistRepository memberTasteArtistRepository;
	private final MemberTasteDecadeRepository memberTasteDecadeRepository;

	public HomeRecommendResponse recommendHome(Long memberId, Integer size) {
		int resolvedSize = resolveSize(size, HOME_DEFAULT_SIZE, HOME_MAX_SIZE);
		TasteSignal taste = loadTasteSignal(memberId);

		Set<Long> wishlistIds = new HashSet<>(wishlistRepository.findProductIdsByMemberId(memberId));
		Set<Long> purchasedIds = new HashSet<>(
				orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(memberId, OrderStatus.PAID_OR_LATER));
		List<Long> recentIds = recentViewService.findRecentProductIds(memberId);

		Set<Long> ownedIds = new HashSet<>(wishlistIds);
		ownedIds.addAll(purchasedIds);
		Set<Long> recentOnlySeedIds = recentIds.stream()
				.filter(id -> !ownedIds.contains(id))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<Long> seedIds = new LinkedHashSet<>(ownedIds);
		seedIds.addAll(recentIds);

		if (taste.isEmpty() && seedIds.isEmpty()) {
			return HomeRecommendResponse.requiresProfile();
		}

		Map<Long, ProductFeature> features = loadFeatures();
		List<ProductFeature> seeds = seedIds.stream()
				.map(features::get)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, Double> coPurchaseScores = aggregateCoPurchaseScores(seedIds);

		// 최근 본 상품도 후보에서 뺀다. 안 그러면 자기 자신과 전 차원이 일치해 최상위로 올라온다.
		List<RankedCandidate> ranked = rank(features, taste, seeds, recentOnlySeedIds, coPurchaseScores, seedIds,
				resolvedSize);

		return HomeRecommendResponse.of(toItems(ranked, memberId));
	}

	public List<RecommendItemResponse> recommendRelated(Long productId, Long memberId, Integer size) {
		int resolvedSize = resolveSize(size, RELATED_DEFAULT_SIZE, RELATED_MAX_SIZE);

		Map<Long, ProductFeature> features = loadFeatures();
		ProductFeature target = features.get(productId);
		if (target == null) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
		}
		if (target.hidden()) {
			throw new BusinessException(ErrorCode.PRODUCT_HIDDEN);
		}

		TasteSignal taste = loadTasteSignal(memberId);
		List<ProductFeature> seeds = List.of(target);
		Set<Long> excludeIds = new HashSet<>();
		excludeIds.add(productId);
		Map<Long, Double> coPurchaseScores;

		if (memberId == null) {
			coPurchaseScores = Map.of();
		} else {
			excludeIds.addAll(wishlistRepository.findProductIdsByMemberId(memberId));
			excludeIds.addAll(
					orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(memberId, OrderStatus.PAID_OR_LATER));
			coPurchaseScores = boughtTogetherRedisService.findScores(productId);
		}

		List<RankedCandidate> ranked = rank(features, taste, seeds, Set.of(), coPurchaseScores, excludeIds,
				resolvedSize);

		return toItems(ranked, memberId);
	}

	private TasteSignal loadTasteSignal(Long memberId) {
		if (memberId == null) {
			return TasteSignal.empty();
		}
		return memberTasteProfileRepository.findByMemberId(memberId)
				.map(this::toTasteSignal)
				.orElseGet(TasteSignal::empty);
	}

	private TasteSignal toTasteSignal(MemberTasteProfile profile) {
		Long profileId = profile.getId();
		Set<Long> artistIds = memberTasteArtistRepository.findAllByProfileId(profileId).stream()
				.map(artist -> artist.getArtist().getId())
				.collect(Collectors.toSet());
		Set<Long> genreIds = memberTasteGenreRepository.findAllByProfileId(profileId).stream()
				.map(genre -> genre.getGenre().getId())
				.collect(Collectors.toSet());
		Set<Decade> decades = memberTasteDecadeRepository.findAllByProfileId(profileId).stream()
				.map(MemberTasteDecade::getDecade)
				.collect(Collectors.toSet());
		return new TasteSignal(artistIds, genreIds, decades);
	}

	// 상품이 수백 개 규모라 캐시 없이 요청마다 findProductFeatures() 를 한 번 읽어 쓴다.
	private Map<Long, ProductFeature> loadFeatures() {
		Map<Long, ProductFeature> features = new LinkedHashMap<>();
		for (ProductFeatureRow row : recommendQueryMapper.findProductFeatures()) {
			ProductFeature feature = ProductFeature.from(row);
			features.put(feature.id(), feature);
		}
		return features;
	}

	private Map<Long, Double> aggregateCoPurchaseScores(Set<Long> seedIds) {
		if (seedIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Double> totalScores = new LinkedHashMap<>();
		for (Map<Long, Double> scoresByCandidate : boughtTogetherRedisService.findScores(seedIds).values()) {
			scoresByCandidate.forEach((candidateId, score) -> totalScores.merge(candidateId, score, Double::sum));
		}
		return totalScores;
	}

	private List<RankedCandidate> rank(Map<Long, ProductFeature> features, TasteSignal taste,
			Collection<ProductFeature> seeds, Set<Long> recentOnlySeedIds, Map<Long, Double> coPurchaseScores,
			Set<Long> excludeIds, int size) {
		return features.values().stream()
				.filter(feature -> !feature.hidden())
				.filter(feature -> !excludeIds.contains(feature.id()))
				.map(feature -> new RankedCandidate(feature, recommendScorer.score(feature, taste, seeds,
						recentOnlySeedIds, coPurchaseScores.getOrDefault(feature.id(), 0.0))))
				.filter(candidate -> candidate.score().totalScore() > 0)
				.sorted(RANKING_COMPARATOR)
				.limit(size)
				.toList();
	}

	private List<RecommendItemResponse> toItems(List<RankedCandidate> ranked, Long memberId) {
		if (ranked.isEmpty()) {
			return List.of();
		}
		List<Long> ids = ranked.stream().map(candidate -> candidate.feature().id()).toList();
		Map<Long, RecommendScorer.ScoreResult> scoreById = ranked.stream()
				.collect(Collectors.toMap(candidate -> candidate.feature().id(), RankedCandidate::score));
		Map<Long, ProductSummaryResponse> summaryById = recommendQueryMapper.findSummariesByIds(ids, memberId)
				.stream()
				.collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));

		// findProductFeatures() 조회 이후 HIDDEN 전환 등으로 요약이 빠진 id 는 자연히 제외된다.
		return ids.stream()
				.map(summaryById::get)
				.filter(Objects::nonNull)
				.map(summary -> new RecommendItemResponse(summary, scoreById.get(summary.id()).topReasons()))
				.toList();
	}

	private int resolveSize(Integer size, int defaultSize, int maxSize) {
		if (size == null) {
			return defaultSize;
		}
		if (size < 1 || size > maxSize) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		return size;
	}

	private record RankedCandidate(ProductFeature feature, RecommendScorer.ScoreResult score) {
	}
}
