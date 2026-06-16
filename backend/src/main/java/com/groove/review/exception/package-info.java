/**
 * 리뷰 도메인 예외 — DomainException 계열로 GlobalExceptionHandler 가 ProblemDetail 로 변환한다.
 * 404(ReviewNotFoundException), 403(ReviewNotOwnedException), 422(ReviewOrderNotDeliveredException,
 * AlbumNotInOrderException), 409(DuplicateReviewException).
 */
package com.groove.review.exception;
