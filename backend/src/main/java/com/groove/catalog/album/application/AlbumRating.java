package com.groove.catalog.album.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 앨범 한 건의 리뷰 집계 값. averageRating 은 소수 1자리 반올림(리뷰 없으면 null), reviewCount 는 리뷰 수(없으면 0).
 */
public record AlbumRating(Double averageRating, long reviewCount) {

    /** 평점 평균 노출 소수 자릿수. */
    private static final int RATING_SCALE = 1;

    /** 리뷰가 한 건도 없는 앨범의 기본값. */
    public static final AlbumRating NONE = new AlbumRating(null, 0L);

    /** 리뷰 집계 원값(평균·개수)으로부터 노출용 AlbumRating 을 만든다(평균은 소수 1자리 반올림). */
    public static AlbumRating of(double averageRating, long reviewCount) {
        double rounded = BigDecimal.valueOf(averageRating)
                .setScale(RATING_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
        return new AlbumRating(rounded, reviewCount);
    }
}
