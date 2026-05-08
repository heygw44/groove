package com.groove.catalog.genre.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 장르 수정 요청 (API §3.9 admin 카탈로그 CRUD).
 *
 * <p>현재 변경 가능한 필드는 {@code name} 한 개라 별도 분리 없이 단일 record 로 둔다.
 * 필드가 늘어나면 {@code GenreCreateRequest} 와 분리된 검증 정책 도입을 검토한다.
 */
public record GenreUpdateRequest(
        @NotBlank
        @Size(min = 1, max = 50)
        String name
) {
}
