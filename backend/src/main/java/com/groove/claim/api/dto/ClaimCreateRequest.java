package com.groove.claim.api.dto;

import com.groove.claim.application.ClaimCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 반품 접수 요청 (POST /claims). 반품할 OrderItem 과 수량을 항목 목록으로 받는다. */
public record ClaimCreateRequest(
        @Schema(description = "반품 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        @NotBlank String orderNumber,
        @Schema(description = "반품 사유", example = "단순 변심")
        @NotBlank @Size(max = 500) String reason,
        @Schema(description = "반품 항목 (1개 이상)")
        @NotEmpty @Valid List<Line> items
) {

    /** 반품 항목 1줄. */
    public record Line(
            @Schema(description = "반품할 주문 항목 식별자", example = "10")
            @NotNull @Positive Long orderItemId,
            @Schema(description = "반품 수량 (1 이상)", example = "1")
            @Min(1) int quantity
    ) {
    }

    public ClaimCreateCommand toCommand(Long memberId) {
        List<ClaimCreateCommand.Line> lines = items.stream()
                .map(line -> new ClaimCreateCommand.Line(line.orderItemId(), line.quantity()))
                .toList();
        return new ClaimCreateCommand(memberId, orderNumber, reason, lines);
    }
}
