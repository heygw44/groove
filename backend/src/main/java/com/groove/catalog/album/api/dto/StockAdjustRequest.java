package com.groove.catalog.album.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 재고 조정 요청 (API §3.9 PATCH /admin/albums/{id}/stock).
 *
 * <p>{@code delta} 는 음수도 허용된다 (반품·재고 감소). 결과 재고가 음수면 도메인 메서드에서 400 으로 거절.
 * 0 은 비즈니스적으로 무의미하지만 별도 거절하지 않는다 — 단순 통과.
 */
public record StockAdjustRequest(
        @NotNull
        Integer delta
) {
}
