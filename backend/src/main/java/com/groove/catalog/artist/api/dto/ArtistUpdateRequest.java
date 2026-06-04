package com.groove.catalog.artist.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 아티스트 수정 요청 (API §3.9 admin 카탈로그 CRUD).
 *
 * <p>PUT 전체 교체 정책 — Genre/Label 과 동일. {@code description} 을 {@code null} 로 보내면
 * 명시적 지움으로 처리된다.
 */
public record ArtistUpdateRequest(
        @NotBlank
        @Size(min = 1, max = 200)
        String name,

        @Size(max = 2000)
        String description
) {
}
