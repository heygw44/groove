package com.groove.catalog.genre.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 장르 수정 요청. 변경 가능한 필드는 name 한 개. */
public record GenreUpdateRequest(
        @Schema(description = "장르 이름 (UNIQUE)", example = "Jazz", maxLength = 50)
        @NotBlank
        @Size(min = 1, max = 50)
        String name
) {
}
