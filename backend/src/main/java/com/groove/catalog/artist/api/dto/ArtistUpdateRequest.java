package com.groove.catalog.artist.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 아티스트 수정 요청 (PUT 전체 교체). description 을 null 로 보내면 명시적 지움. */
public record ArtistUpdateRequest(
        @Schema(description = "아티스트 이름", example = "Daft Punk", maxLength = 200)
        @NotBlank
        @Size(min = 1, max = 200)
        String name,

        @Schema(description = "아티스트 소개 (null 이면 명시적 지움)", example = "프랑스의 전자음악 듀오", maxLength = 2000)
        @Size(max = 2000)
        String description
) {
}
