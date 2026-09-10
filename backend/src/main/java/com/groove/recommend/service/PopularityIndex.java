package com.groove.recommend.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.groove.product.entity.ProductStatus;

/**
 * 베이지안 평점 스무딩과 판매량을 합친 인기 점수. {@link ProductFeatureCache} 스냅샷에서 계산해 동점 처리와
 * 콜드스타트 폴백이 같은 공식을 공유하게 한다 — SQL 과 Java 두 곳에 같은 계산이 있으면 갈라지기 쉽다.
 *
 * <p>{@code bayes(p) = (v·R + m·C) / (v + m)}, {@code popularity(p) = α·ln(1+sold)/ln(1+maxSold)
 * + (1-α)·bayes(p)/5.0}. {@code m}(prior 강도)과 {@code α}(판매량 비중)는 향후 스윕 대상이라 상수로 뺐다.
 */
public final class PopularityIndex {

	private static final int BAYESIAN_PRIOR_M = 5;
	private static final double POPULARITY_ALPHA = 0.6;
	private static final double MAX_RATING = 5.0;

	private final double globalAverageRating;
	private final long maxSoldQuantity;

	private PopularityIndex(double globalAverageRating, long maxSoldQuantity) {
		this.globalAverageRating = globalAverageRating;
		this.maxSoldQuantity = maxSoldQuantity;
	}

	/** 스냅샷에서 리뷰 1건 이상 상품의 평점 평균(C)과 최대 판매량을 구해 인덱스를 만든다. */
	public static PopularityIndex from(Collection<ProductFeature> features) {
		double globalAverageRating = features.stream()
				.filter(feature -> feature.reviewCount() > 0)
				.mapToDouble(ProductFeature::averageRating)
				.average()
				.orElse(0.0);
		long maxSoldQuantity = features.stream()
				.mapToLong(ProductFeature::soldQuantity)
				.max()
				.orElse(0L);
		return new PopularityIndex(globalAverageRating, maxSoldQuantity);
	}

	/** 베이지안 평균. 리뷰가 없는 상품(v=0)은 그대로 C 가 된다. */
	public double bayes(ProductFeature feature) {
		int reviewCount = feature.reviewCount();
		double rating = feature.averageRating() == null ? 0.0 : feature.averageRating();
		return (reviewCount * rating + BAYESIAN_PRIOR_M * globalAverageRating) / (reviewCount + BAYESIAN_PRIOR_M);
	}

	/** 판매량과 베이지안 평점을 합친 인기 점수. 판매량이 전무하면(운영 초기) bayes 단독으로 수렴한다. */
	public double popularity(ProductFeature feature) {
		double salesTerm = maxSoldQuantity == 0 ? 0.0
				: Math.log1p(feature.soldQuantity()) / Math.log1p(maxSoldQuantity);
		double ratingTerm = bayes(feature) / MAX_RATING;
		return POPULARITY_ALPHA * salesTerm + (1 - POPULARITY_ALPHA) * ratingTerm;
	}

	/** 콜드스타트 폴백용 — ON_SALE 상품만 popularity desc → 최신순 → id desc 로 상위 limit 개 id 를 뽑는다. */
	public List<Long> topOnSaleProductIds(Collection<ProductFeature> features, int limit) {
		Comparator<ProductFeature> comparator = Comparator
				.comparingDouble(this::popularity)
				.reversed()
				.thenComparing(ProductFeature::createdAt, Comparator.reverseOrder())
				.thenComparing(ProductFeature::id, Comparator.reverseOrder());
		return features.stream()
				.filter(feature -> feature.status() == ProductStatus.ON_SALE)
				.sorted(comparator)
				.limit(limit)
				.map(ProductFeature::id)
				.toList();
	}
}
