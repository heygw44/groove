package com.groove.catalog.label.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 레이블 생성 요청 (API §3.9). name 컬럼 길이는 ERD §4.5 기준 100 자.
 */
public record LabelCreateRequest(
        @Schema(description = "레이블 이름 (UNIQUE)", example = "Blue Note", maxLength = 100)
        @NotBlank
        @Size(min = 1, max = 100)
        String name
) {
}
