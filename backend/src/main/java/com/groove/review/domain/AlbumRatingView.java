package com.groove.review.domain;

/**
 * 앨범별 리뷰 집계 프로젝션 — findRatingsByAlbumIds 의 행 단위 결과(읽기 전용 뷰).
 * 리뷰가 없는 앨범은 결과 행이 없으므로 호출 측에서 기본값(null/0)을 채운다.
 */
public interface AlbumRatingView {

    Long getAlbumId();

    /** 평점 산술 평균 — 리뷰가 1건 이상이므로 항상 non-null. */
    double getAverageRating();

    long getReviewCount();
}
