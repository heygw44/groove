package com.groove.catalog.genre.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 장르 생성 요청. name 최대 50 자.
 */
public record GenreCreateRequest(
        @Schema(description = "장르 이름 (UNIQUE)", example = "Jazz", maxLength = 50)
        @NotBlank
        @Size(min = 1, max = 50)
        String name
) {
}
