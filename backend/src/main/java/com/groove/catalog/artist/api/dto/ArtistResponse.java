package com.groove.catalog.artist.api.dto;

import com.groove.catalog.artist.domain.Artist;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 아티스트 응답 DTO (API §3.3 / §3.9).
 */
public record ArtistResponse(
        @Schema(description = "아티스트 ID", example = "1") Long id,
        @Schema(description = "아티스트 이름", example = "Daft Punk") String name,
        @Schema(description = "아티스트 설명", example = "프랑스의 일렉트로닉 듀오") String description,
        @Schema(description = "등록 일시 (ISO-8601)", example = "2026-01-15T09:30:00Z") Instant createdAt,
        @Schema(description = "수정 일시 (ISO-8601)", example = "2026-01-20T14:00:00Z") Instant updatedAt
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
