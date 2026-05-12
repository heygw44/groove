package com.groove.review.api.dto;

import com.groove.review.domain.Review;

import java.time.Instant;

/**
 * 리뷰 응답 (API.md §3.8 / §4 — {@code Review}).
 *
 * <p>{@code POST /reviews} 와 {@code GET /albums/{id}/reviews} 공용. 공개 목록에 노출되므로 작성자 이름은
 * {@link #maskName(String)} 으로 마스킹한다 — 첫 글자만 남기고 나머지는 {@code *} ("김민수" → "김**").
 *
 * @param reviewId   리뷰 식별자
 * @param memberName 작성자 이름 (마스킹)
 * @param rating     평점 1~5
 * @param content    리뷰 내용 — 작성 시 비우면 {@code null}
 * @param createdAt  작성 시각
 */
public record ReviewResponse(
        Long reviewId,
        String memberName,
        int rating,
        String content,
        Instant createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                maskName(review.getMember().getName()),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }

    /**
     * 이름 마스킹 — 첫 글자만 남기고 나머지를 {@code *} 로 치환한다. 1글자 이름은 그대로, blank/null 은 빈 문자열.
     * 예: "김민수" → "김**", "Lee" → "L**", "박" → "박".
     */
    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
