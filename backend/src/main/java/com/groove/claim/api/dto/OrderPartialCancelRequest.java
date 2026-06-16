package com.groove.claim.api.dto;

import com.groove.claim.application.ClaimCreateCommand;
import com.groove.claim.application.OrderPartialCancelCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 관리자 부분 취소 요청 (POST /admin/claims/cancel). 취소할 OrderItem 과 수량을 항목 목록으로 받는다.
 */
public record OrderPartialCancelRequest(
        @Schema(description = "취소 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        @NotBlank String orderNumber,
        @Schema(description = "취소 사유", example = "관리자 직권 부분 취소 — 일부 품목 품절")
        @NotBlank @Size(max = 500) String reason,
        @Schema(description = "취소 항목 (1개 이상)")
        @NotEmpty @Valid List<Line> items
) {

    /** 취소 항목 1줄. */
    public record Line(
            @Schema(description = "취소할 주문 항목 식별자", example = "10")
            @NotNull @Positive Long orderItemId,
            @Schema(description = "취소 수량 (1 이상)", example = "1")
            @Min(1) int quantity
    ) {
    }

    public OrderPartialCancelCommand toCommand() {
        List<ClaimCreateCommand.Line> lines = items.stream()
                .map(line -> new ClaimCreateCommand.Line(line.orderItemId(), line.quantity()))
                .toList();
        return new OrderPartialCancelCommand(orderNumber, reason, lines);
    }
}
