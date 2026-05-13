/**
 * 리뷰 도메인 모델 (W7, ERD §4.14).
 *
 * <p>{@link com.groove.review.domain.Review} 엔티티 + {@link com.groove.review.domain.ReviewRepository}
 * + 카탈로그 평점 집계 프로젝션 {@link com.groove.review.domain.AlbumRatingView}. 배송 완료(DELIVERED 이상)된
 * 본인 회원 주문의 주문 항목(album)에 대한 1~5점 평가이며, 1주문-1상품-1리뷰({@code uk_review_order_album}).
 * 작성 가능 여부 검증은 {@code ReviewService} 에 있고, 도메인은 {@code rating} 범위만 재검증한다.
 */
package com.groove.review.domain;
