package com.groove.catalog.genre.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 장르 생성 요청 (API §3.9 admin 카탈로그 CRUD).
 *
 * <p>{@code name} 컬럼 길이는 ERD §4.4 기준 50 자.
 */
public record GenreCreateRequest(
        @NotBlank
        @Size(min = 1, max = 50)
        String name
) {
}
