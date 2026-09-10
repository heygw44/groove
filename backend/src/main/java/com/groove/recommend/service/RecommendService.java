package com.groove.recommend.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.groove.recommend.dto.RecommendItemResponse;
import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.dto.TasteMatchResponse;
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

	private final RecommendQueryMapper recommendQueryMapper;
	private final RecommendScorer recommendScorer;
	private final RecommendRanker recommendRanker;
	private final ProductFeatureCache productFeatureCache;
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
		HomeSeeds homeSeeds = HomeSeeds.of(wishlistIds, purchasedIds, recentIds);
		Set<Long> seedIds = homeSeeds.seedIds();
		Set<Long> recentOnlySeedIds = homeSeeds.recentOnlySeedIds();

		if (taste.isEmpty() && seedIds.isEmpty()) {
			// findPopularProductIds() 대신 이미 로드된 스냅샷에서 popularity 로 정렬한다.
			// SQL 과 Java 양쪽에 같은 공식을 두면 갈라지기 쉽고, 캐시가 조회보다 싸다.
			Map<Long, ProductFeature> popularFeatures = loadFeatures();
			PopularityIndex popularityIndex = PopularityIndex.from(popularFeatures.values());
			List<Long> popularIds = popularityIndex.topOnSaleProductIds(popularFeatures.values(), resolvedSize);
			return HomeRecommendResponse.requiresProfileWithPopularFallback(toPopularItems(popularIds, memberId));
		}

		Map<Long, ProductFeature> features = loadFeatures();
		List<ProductFeature> seeds = seedIds.stream()
				.map(features::get)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, Double> coPurchaseScores = aggregateCoPurchaseScores(seedIds);

		// 최근 본 상품도 후보에서 뺀다. 안 그러면 자기 자신과 전 차원이 일치해 최상위로 올라온다.
		List<RecommendRanker.RankedCandidate> ranked = recommendRanker.rank(features, taste, seeds,
				recentOnlySeedIds, coPurchaseScores, seedIds, resolvedSize, RecommendRanker.CapPolicy.HOME);

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
		// 같은 앨범의 다른 프레싱은 전 차원이 일치해 관련 상품 최상단을 채운다. 상세에 이미 "다른 프레싱" 섹션이 있어 앨범 단위로 뺀다.
		features.values().stream()
				.filter(feature -> Objects.equals(target.albumId(), feature.albumId()))
				.forEach(feature -> excludeIds.add(feature.id()));
		Map<Long, Double> coPurchaseScores;

		if (memberId == null) {
			coPurchaseScores = Map.of();
		} else {
			excludeIds.addAll(wishlistRepository.findProductIdsByMemberId(memberId));
			excludeIds.addAll(
					orderItemRepository.findProductIdsByMemberIdAndOrderStatusIn(memberId, OrderStatus.PAID_OR_LATER));
			coPurchaseScores = boughtTogetherRedisService.findScores(productId);
		}

		// 같은 아티스트의 다른 앨범은 관련 상품에서 정당한 추천이라 캡을 걸지 않는다.
		List<RecommendRanker.RankedCandidate> ranked = recommendRanker.rank(features, taste, seeds, Set.of(),
				coPurchaseScores, excludeIds, resolvedSize, RecommendRanker.CapPolicy.UNCAPPED);

		return toItems(ranked, memberId);
	}

	/** 상품별 취향 매칭. 프로필이 없으면 전부 matched=false, 목록에 없는 상품 id 도 none() 으로 채운다. */
	public Map<Long, TasteMatchResponse> matchTaste(Long memberId, Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}

		Map<Long, TasteMatchResponse> result = new LinkedHashMap<>();
		TasteSignal taste = loadTasteSignal(memberId);
		if (taste.isEmpty()) {
			productIds.forEach(id -> result.put(id, TasteMatchResponse.none()));
			return result;
		}

		Map<Long, ProductFeature> features = loadFeatures();
		for (Long productId : productIds) {
			ProductFeature feature = features.get(productId);
			if (feature == null) {
				result.put(productId, TasteMatchResponse.none());
				continue;
			}
			RecommendScorer.ScoreResult scoreResult = recommendScorer.scoreTaste(feature, taste);
			result.put(productId, recommendScorer.matchesTaste(scoreResult)
					? new TasteMatchResponse(true, scoreResult.topReasons())
					: TasteMatchResponse.none());
		}
		return result;
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

	private Map<Long, ProductFeature> loadFeatures() {
		return productFeatureCache.get();
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

	private List<RecommendItemResponse> toItems(List<RecommendRanker.RankedCandidate> ranked, Long memberId) {
		if (ranked.isEmpty()) {
			return List.of();
		}
		List<Long> ids = ranked.stream().map(candidate -> candidate.feature().id()).toList();
		Map<Long, RecommendScorer.ScoreResult> scoreById = ranked.stream()
				.collect(Collectors.toMap(candidate -> candidate.feature().id(),
						RecommendRanker.RankedCandidate::score));
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

	private List<RecommendItemResponse> toPopularItems(List<Long> ids, Long memberId) {
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Long, ProductSummaryResponse> summaryById = recommendQueryMapper.findSummariesByIds(ids, memberId)
				.stream()
				.collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));

		return ids.stream()
				.map(summaryById::get)
				.filter(Objects::nonNull)
				.map(summary -> new RecommendItemResponse(summary, List.of(RecommendReason.POPULAR)))
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
}
