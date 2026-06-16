package com.groove.catalog.album.api.dto;

import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 앨범 생성 요청. label/coverImageUrl/description 은 nullable. */
public record AlbumCreateRequest(
        @Schema(description = "앨범 제목", example = "Kind of Blue", maxLength = 300)
        @NotBlank
        @Size(min = 1, max = 300)
        String title,

        @Schema(description = "아티스트 ID (FK, 필수)", example = "1")
        @NotNull
        @Positive
        Long artistId,

        @Schema(description = "장르 ID (FK, 필수)", example = "3")
        @NotNull
        @Positive
        Long genreId,

        @Schema(description = "레이블 ID (FK, nullable)", example = "5")
        @Positive
        Long labelId,

        @Schema(description = "발매 연도 (1900~2100)", example = "1959")
        @NotNull
        @Min(1900)
        @Max(2100)
        Short releaseYear,

        @Schema(description = "앨범 포맷", example = "LP")
        @NotNull
        AlbumFormat format,

        @Schema(description = "판매 가격 (원, 0 이상)", example = "38000")
        @NotNull
        @Min(0)
        Long price,

        @Schema(description = "초기 재고 수량 (0 이상)", example = "100")
        @NotNull
        @Min(0)
        Integer stock,

        @Schema(description = "판매 상태", example = "SELLING")
        @NotNull
        AlbumStatus status,

        @Schema(description = "한정반 여부", example = "false")
        @NotNull
        Boolean isLimited,

        @Schema(description = "커버 이미지 URL (nullable)", example = "https://cdn.groove.example/covers/kob.jpg", maxLength = 500)
        @Size(max = 500)
        String coverImageUrl,

        @Schema(description = "앨범 설명 (nullable)", example = "Miles Davis 의 모달 재즈 명반", maxLength = 2000)
        @Size(max = 2000)
        String description
) {
}
