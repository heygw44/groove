package com.groove.catalog.genre.api.dto;

import com.groove.catalog.genre.domain.Genre;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record GenreResponse(
        @Schema(description = "장르 ID", example = "3") Long id,
        @Schema(description = "장르 이름", example = "Electronic") String name,
        @Schema(description = "등록 일시 (ISO-8601)", example = "2026-01-15T09:30:00Z") Instant createdAt,
        @Schema(description = "수정 일시 (ISO-8601)", example = "2026-01-20T14:00:00Z") Instant updatedAt
) {
    public static GenreResponse from(Genre genre) {
        return new GenreResponse(
                genre.getId(),
                genre.getName(),
                genre.getCreatedAt(),
                genre.getUpdatedAt()
        );
    }
}
