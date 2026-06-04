package com.groove.order.api.dto;

import jakarta.validation.constraints.Size;

/**
 * 주문 취소 요청 (API.md §3.5).
 *
 * <p>{@code reason} 은 선택 — null 또는 공백 허용. 길이만 제한한다 (DB 컬럼 길이 = 500).
 */
public record OrderCancelRequest(
        @Size(max = 500) String reason
) {
}
