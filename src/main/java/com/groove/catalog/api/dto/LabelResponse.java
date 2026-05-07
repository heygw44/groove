package com.groove.catalog.api.dto;

import com.groove.catalog.domain.Label;

import java.time.Instant;

/**
 * 레이블 응답 DTO (API §3.9).
 */
public record LabelResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt
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
