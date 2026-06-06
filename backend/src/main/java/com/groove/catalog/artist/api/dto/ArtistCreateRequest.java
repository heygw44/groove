package com.groove.catalog.artist.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 아티스트 생성 요청 (API §3.9 admin 카탈로그 CRUD).
 *
 * <p>{@code name} 컬럼 길이는 ERD §4.3 기준 200 자.
 * {@code description} 은 nullable (DB TEXT NULL); 길이 상한은 애플리케이션 단에서 2000자로 cap.
 */
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
