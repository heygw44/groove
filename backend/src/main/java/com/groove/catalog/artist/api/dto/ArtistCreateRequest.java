package com.groove.catalog.artist.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 아티스트 생성 요청. name 최대 200자, description 은 nullable·최대 2000자. */
public record ArtistCreateRequest(
        @Schema(description = "아티스트 이름", example = "Daft Punk", maxLength = 200)
        @NotBlank
        @Size(min = 1, max = 200)
        String name,

        @Schema(description = "아티스트 소개 (nullable)", example = "프랑스의 전자음악 듀오", maxLength = 2000)
        @Size(max = 2000)
        String description
) {
}
