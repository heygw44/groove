package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 앨범 전체 갱신 요청 (PUT 정책). stock 은 본 요청에서 제외 — 변경은 PATCH /stock 으로만 허용한다.
 */
public record AlbumUpdateRequest(
        @NotBlank
        @Size(min = 1, max = 300)
        String title,

        @NotNull
        @Positive
        Long artistId,

        @NotNull
        @Positive
        Long genreId,

        @Positive
        Long labelId,

        @NotNull
        @Min(1900)
        @Max(2100)
        Short releaseYear,

        @NotNull
        AlbumFormat format,

        @NotNull
        @Min(0)
        Long price,

        @NotNull
        AlbumStatus status,

        @NotNull
        Boolean isLimited,

        @Size(max = 500)
        String coverImageUrl,

        @Size(max = 2000)
        String description
) {
}
