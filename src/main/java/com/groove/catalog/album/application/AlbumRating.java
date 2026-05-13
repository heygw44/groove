package com.groove.catalog.album.application;

import com.groove.review.domain.AlbumRatingView;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 앨범 한 건의 리뷰 집계 값 — 카탈로그 응답({@code AlbumSummary}/{@code AlbumDetail})의
 * {@code averageRating}/{@code reviewCount} 채움용 읽기 모델.
 *
 * <p>{@link AlbumRatingView}(JPA 프로젝션)를 응답에 그대로 노출하지 않고 이 값 객체로 한 번 감싼다 —
 * 평균은 소수 1자리 반올림(API.md 예시 {@code 4.8}), 리뷰가 없는 앨범은 {@link #NONE}(평균 {@code null}, 수 {@code 0}).
 *
 * @param averageRating 소수 1자리로 반올림한 평점 평균 — 리뷰가 없으면 {@code null}
 * @param reviewCount   리뷰 수 — 리뷰가 없으면 {@code 0}
 */
public record AlbumRating(Double averageRating, long reviewCount) {

    /** 평점 평균을 노출할 때의 소수 자릿수 (API.md 예시 {@code 4.8}). */
    private static final int RATING_SCALE = 1;

    /** 리뷰가 한 건도 없는 앨범의 기본값. */
    public static final AlbumRating NONE = new AlbumRating(null, 0L);

    public static AlbumRating from(AlbumRatingView view) {
        double rounded = BigDecimal.valueOf(view.getAverageRating())
                .setScale(RATING_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
        return new AlbumRating(rounded, view.getReviewCount());
    }
}
