package com.groove.catalog.label.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 레이블 수정 요청. 변경 가능한 필드가 name 한 개라 분리하지 않았다.
 */
public record LabelUpdateRequest(
        @Schema(description = "레이블 이름 (UNIQUE)", example = "Blue Note", maxLength = 100)
        @NotBlank
        @Size(min = 1, max = 100)
        String name
) {
}
