/**
 * 리뷰 REST 진입점 — {@code POST /api/v1/reviews}, {@code DELETE /api/v1/reviews/{reviewId}} (인증 회원 전용,
 * {@link com.groove.review.api.ReviewController}) 와 {@code GET /api/v1/albums/{id}/reviews} (Public,
 * {@link com.groove.review.api.AlbumReviewController}).
 *
 * <p>비트랜잭션 — {@code ReviewService} 가 트랜잭션 경계를 갖는다. 정렬 키는 {@code createdAt} 화이트리스트로 강제한다.
 */
package com.groove.review.api;
