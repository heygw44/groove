package com.groove.recommend.service;

import com.groove.recommend.dto.RecommendReason;

/**
 * 추천 점수 가중치. 컴포넌트를 래퍼 타입으로 두고 compact 생성자에서 null 을 기본값으로 채운다.
 * {@code groove.recommend.weights.same-genre: 3} 처럼 일부 필드만 yaml 에 있어도 나머지가
 * 0 으로 깨지지 않게 하기 위함이다.
 */
public record RecommendWeights(Integer tasteArtist, Integer sameArtist, Integer tasteGenre, Integer sameGenre,
		Integer sameLabel, Integer tasteDecade, Integer sameDecade, Double coPurchase, Double tasteMatchThreshold) {

	/** yaml 설정이 없을 때 쓰는 기본값 인스턴스. */
	public static final RecommendWeights DEFAULT = new RecommendWeights(null, null, null, null, null, null, null,
			null, null);

	public RecommendWeights {
		tasteArtist = tasteArtist == null ? 5 : tasteArtist;
		sameArtist = sameArtist == null ? 4 : sameArtist;
		tasteGenre = tasteGenre == null ? 3 : tasteGenre;
		sameGenre = sameGenre == null ? 2 : sameGenre;
		sameLabel = sameLabel == null ? 2 : sameLabel;
		tasteDecade = tasteDecade == null ? 1 : tasteDecade;
		sameDecade = sameDecade == null ? 1 : sameDecade;
		coPurchase = coPurchase == null ? 2.0 : coPurchase;
		tasteMatchThreshold = tasteMatchThreshold == null ? 3.0 : tasteMatchThreshold;
	}

	/** 콘텐츠 매칭 이유(SAME_x, TASTE_x)의 가중치. 그 외 이유는 지원하지 않는다. */
	public double weightOf(RecommendReason reason) {
		return switch (reason) {
			case TASTE_ARTIST -> tasteArtist;
			case SAME_ARTIST -> sameArtist;
			case TASTE_GENRE -> tasteGenre;
			case SAME_GENRE -> sameGenre;
			case SAME_LABEL -> sameLabel;
			case TASTE_DECADE -> tasteDecade;
			case SAME_DECADE -> sameDecade;
			default -> throw new IllegalArgumentException("가중치가 없는 추천 이유: " + reason);
		};
	}

	/** 해당 차원의 가중치를 0 으로 만든 사본을 만든다. ablation 측정용. */
	public RecommendWeights withZeroed(RecommendReason reason) {
		if (reason == RecommendReason.BOUGHT_TOGETHER) {
			return withCoPurchase(0);
		}
		return with(reason, 0);
	}

	/** 공동구매 가중치만 바꾼 사본을 만든다. 콘텐츠 차원과 달리 실수라 {@link #with} 로는 못 바꾼다. */
	public RecommendWeights withCoPurchase(double value) {
		return new RecommendWeights(tasteArtist, sameArtist, tasteGenre, sameGenre, sameLabel, tasteDecade,
				sameDecade, value, tasteMatchThreshold);
	}

	/** 해당 차원의 가중치만 바꾼 사본을 만든다. 가중치 스윕용. */
	public RecommendWeights with(RecommendReason reason, int value) {
		return switch (reason) {
			case TASTE_ARTIST -> new RecommendWeights(value, sameArtist, tasteGenre, sameGenre, sameLabel,
					tasteDecade, sameDecade, coPurchase, tasteMatchThreshold);
			case SAME_ARTIST -> new RecommendWeights(tasteArtist, value, tasteGenre, sameGenre, sameLabel,
					tasteDecade, sameDecade, coPurchase, tasteMatchThreshold);
			case TASTE_GENRE -> new RecommendWeights(tasteArtist, sameArtist, value, sameGenre, sameLabel,
					tasteDecade, sameDecade, coPurchase, tasteMatchThreshold);
			case SAME_GENRE -> new RecommendWeights(tasteArtist, sameArtist, tasteGenre, value, sameLabel,
					tasteDecade, sameDecade, coPurchase, tasteMatchThreshold);
			case SAME_LABEL -> new RecommendWeights(tasteArtist, sameArtist, tasteGenre, sameGenre, value,
					tasteDecade, sameDecade, coPurchase, tasteMatchThreshold);
			case TASTE_DECADE -> new RecommendWeights(tasteArtist, sameArtist, tasteGenre, sameGenre, sameLabel,
					value, sameDecade, coPurchase, tasteMatchThreshold);
			case SAME_DECADE -> new RecommendWeights(tasteArtist, sameArtist, tasteGenre, sameGenre, sameLabel,
					tasteDecade, value, coPurchase, tasteMatchThreshold);
			default -> throw new IllegalArgumentException("가중치가 없는 추천 이유: " + reason);
		};
	}
}
