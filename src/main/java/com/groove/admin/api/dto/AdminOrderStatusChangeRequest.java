package com.groove.admin.api.dto;

import com.groove.order.domain.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 주문 상태 강제 전환 요청 (이슈 #69, PATCH /api/v1/admin/orders/{orderNumber}/status).
 *
 * <p>{@code reason} 은 필수 — 강제 전환은 정상 흐름을 우회하는 운영 조작이므로 사유를 반드시 남긴다
 * (CANCELLED 전이 시 {@code Order.cancelledReason} 으로도 기록). 잘못된 enum 문자열은 바인딩 단계에서 400.
 */
public record AdminOrderStatusChangeRequest(
        @NotNull OrderStatus target,
        @NotBlank @Size(max = 500) String reason
) {
}
