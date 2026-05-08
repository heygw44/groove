package com.groove.catalog.genre.api.dto;

import com.groove.catalog.genre.domain.Genre;

import java.time.Instant;

/**
 * 장르 응답 DTO (API §3.9).
 */
public record GenreResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt
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
