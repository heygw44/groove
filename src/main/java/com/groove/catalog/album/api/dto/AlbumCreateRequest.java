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
 * 앨범 생성 요청 (API §3.9 admin 카탈로그).
 *
 * <p>title 길이는 ERD §4.6 기준 300, label 은 nullable. release_year 는 ERD 표기상
 * SMALLINT — 1900 ~ 2100 으로 합리적 범위 캡 (LP 시장 시연 데이터 기준).
 * description/cover_image_url 은 nullable, 길이 상한은 DB 컬럼과 동일하게 cap.
 */
public record AlbumCreateRequest(
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
        @Min(0)
        Integer stock,

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
