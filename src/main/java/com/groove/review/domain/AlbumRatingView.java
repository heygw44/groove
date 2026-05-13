package com.groove.review.domain;

/**
 * 앨범별 리뷰 집계 프로젝션 — {@link ReviewRepository#findRatingsByAlbumIds(java.util.Collection)} 의 행 단위 결과.
 *
 * <p>카탈로그 응답({@code AlbumSummary}/{@code AlbumDetail}) 의 {@code averageRating}/{@code reviewCount} 를
 * 채우기 위한 읽기 전용 뷰다. 리뷰가 한 건도 없는 앨범은 결과 행 자체가 없으므로 호출 측에서 기본값(null/0)을 채운다.
 *
 * @see com.groove.catalog.album.application.AlbumRating
 */
public interface AlbumRatingView {

    Long getAlbumId();

    /** 평점 산술 평균 — 리뷰가 1건 이상이므로 항상 non-null. 반올림은 호출 측({@code AlbumRating}) 책임. */
    double getAverageRating();

    long getReviewCount();
}
