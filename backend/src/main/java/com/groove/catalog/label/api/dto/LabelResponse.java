package com.groove.catalog.label.api.dto;

import com.groove.catalog.label.domain.Label;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 레이블 응답 DTO (API §3.9).
 */
public record LabelResponse(
        @Schema(description = "레이블 ID", example = "7") Long id,
        @Schema(description = "레이블 이름", example = "Columbia Records") String name,
        @Schema(description = "등록 일시 (ISO-8601)", example = "2026-01-15T09:30:00Z") Instant createdAt,
        @Schema(description = "수정 일시 (ISO-8601)", example = "2026-01-20T14:00:00Z") Instant updatedAt
) {
    public static LabelResponse from(Label label) {
        return new LabelResponse(
                label.getId(),
                label.getName(),
                label.getCreatedAt(),
                label.getUpdatedAt()
        );
    }
}
