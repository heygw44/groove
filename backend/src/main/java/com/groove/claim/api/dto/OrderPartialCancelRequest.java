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
 * 관리자 부분 취소 요청 (#238 — POST /admin/claims/cancel).
 *
 * <p>발송 전({@code PAID}/{@code PREPARING}) 주문의 일부 품목/수량을 취소·환불한다. 부분 취소를 지원하므로 취소할
 * OrderItem 과 수량을 항목 목록으로 받는다. 같은 {@code orderItemId} 가 여러 번 와도 서비스가 수량을 합산해 취소가능
 * 수량 가드를 적용한다. 관리자 권한 경로라 회원 식별자는 받지 않는다.
 *
 * @param orderNumber 취소 대상 주문 번호
 * @param reason      취소 사유 (필수, 500자 이하)
 * @param items       취소 항목 — 1개 이상
 */
public record OrderPartialCancelRequest(
        @Schema(description = "취소 대상 주문 번호", example = "ORD-20260606-A1B2C3")
        @NotBlank String orderNumber,
        @Schema(description = "취소 사유", example = "관리자 직권 부분 취소 — 일부 품목 품절")
        @NotBlank @Size(max = 500) String reason,
        @Schema(description = "취소 항목 (1개 이상)")
        @NotEmpty @Valid List<Line> items
) {

    /**
     * 취소 항목 1줄.
     *
     * @param orderItemId 취소할 주문 항목 식별자
     * @param quantity    취소 수량 (1 이상)
     */
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
