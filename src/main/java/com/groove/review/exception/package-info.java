/**
 * 리뷰 도메인 예외 — {@code DomainException} 계열로 {@code GlobalExceptionHandler} 가 ProblemDetail 로 변환한다.
 * 404({@code ReviewNotFoundException}), 403({@code ReviewNotOwnedException}), 422({@code ReviewOrderNotDeliveredException},
 * {@code AlbumNotInOrderException}), 409({@code DuplicateReviewException}).
 */
package com.groove.review.exception;
