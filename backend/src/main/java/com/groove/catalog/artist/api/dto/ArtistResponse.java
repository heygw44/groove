package com.groove.catalog.artist.api.dto;

import com.groove.catalog.artist.domain.Artist;

import java.time.Instant;

/**
 * 아티스트 응답 DTO (API §3.3 / §3.9).
 */
public record ArtistResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static ArtistResponse from(Artist artist) {
        return new ArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getDescription(),
                artist.getCreatedAt(),
                artist.getUpdatedAt()
        );
    }
}
