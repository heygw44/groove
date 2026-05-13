package com.groove.review.api.dto;

import com.groove.review.application.ReviewCreateCommand;
import com.groove.review.domain.Review;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 리뷰 작성 요청 (API.md §3.8 — POST /reviews).
 *
 * <p>{@code content} 는 선택 — 본문 길이는 DB {@code TEXT} 컬럼 한도보다 훨씬 작게(2000자) 잘라 막는다.
 * {@code rating} 범위는 {@link Review#MIN_RATING}~{@link Review#MAX_RATING} 와 일치한다 (도메인이 최종 재검증).
 *
 * @param orderNumber 리뷰 대상 주문 번호
 * @param albumId     리뷰 대상 앨범 식별자
 * @param rating      평점 1~5
 * @param content     리뷰 내용 — nullable, 2000자 이하
 */
public record ReviewCreateRequest(
        @NotBlank String orderNumber,
        @NotNull @Positive Long albumId,
        @Min(Review.MIN_RATING) @Max(Review.MAX_RATING) int rating,
        @Size(max = 2000) String content
) {

    public ReviewCreateCommand toCommand(Long memberId) {
        return new ReviewCreateCommand(memberId, orderNumber, albumId, rating, content);
    }
}
