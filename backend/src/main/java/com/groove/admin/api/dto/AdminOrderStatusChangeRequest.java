package com.groove.admin.api.dto;

import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 주문 상태 강제 전환 요청 (PATCH /api/v1/admin/orders/{orderNumber}/status).
 * reason 은 필수다(CANCELLED 전이 시 Order.cancelledReason 으로도 기록). 잘못된 enum 문자열은 400.
 */
public record AdminOrderStatusChangeRequest(
        @Schema(description = "강제 전환할 목표 상태 — 전진 전이만 허용(PREPARING/SHIPPED/DELIVERED/COMPLETED)",
                example = "DELIVERED")
        @NotNull OrderStatus target,

        @Schema(description = "강제 전환 사유 — 필수 (운영 감사 추적, 최대 500자)", example = "고객 요청으로 배송 완료 처리")
        @NotBlank @Size(max = MAX_REASON_LENGTH) String reason
) {

    /** 운영 사유 문자열 최대 길이 — Order.cancelled_reason / Payment.failure_reason DB 컬럼 길이와 정렬한다. */
    public static final int MAX_REASON_LENGTH = 500;
}
