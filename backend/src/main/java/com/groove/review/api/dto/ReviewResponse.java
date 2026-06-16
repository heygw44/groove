package com.groove.review.api.dto;

import com.groove.review.domain.Review;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 리뷰 응답. POST /reviews 와 GET /albums/{id}/reviews 공용. 작성자 이름은 maskName 으로 마스킹한다.
 */
public record ReviewResponse(
        @Schema(description = "리뷰 식별자", example = "100")
        Long reviewId,
        @Schema(description = "작성자 이름 (마스킹 — 첫 글자만 노출)", example = "김**")
        String memberName,
        @Schema(description = "평점 (1~5)", example = "5")
        int rating,
        @Schema(description = "리뷰 내용 (비어 있으면 null)", example = "음질이 정말 좋아요. 추천합니다!")
        String content,
        @Schema(description = "작성 시각 (ISO-8601 UTC)", example = "2026-07-01T10:30:00Z")
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
     * 이름 마스킹 — 첫 글자만 남기고 나머지를 * 로 치환한다. 1글자 이름은 그대로, blank/null 은 빈 문자열.
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
