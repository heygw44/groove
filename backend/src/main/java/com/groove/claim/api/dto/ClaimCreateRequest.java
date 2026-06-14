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

/**
 * 반품 접수 요청 (#239 — POST /claims).
 *
 * <p>부분 반품을 지원하므로 반품할 OrderItem 과 수량을 항목 목록으로 받는다. 같은 {@code orderItemId} 가 여러 번
 * 와도 서비스가 수량을 합산해 잔여 수량 가드를 적용한다.
 *
 * @param orderNumber 반품 대상 주문 번호
 * @param reason      반품 사유 (필수, 500자 이하)
 * @param items       반품 항목 — 1개 이상
 */
public record ClaimCreateRequest(
        @Schema(description = "반품 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        @NotBlank String orderNumber,
        @Schema(description = "반품 사유", example = "단순 변심")
        @NotBlank @Size(max = 500) String reason,
        @Schema(description = "반품 항목 (1개 이상)")
        @NotEmpty @Valid List<Line> items
) {

    /**
     * 반품 항목 1줄.
     *
     * @param orderItemId 반품할 주문 항목 식별자
     * @param quantity    반품 수량 (1 이상)
     */
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
