package com.groove.payment.api.dto;

import com.groove.order.domain.OrderNumberFormat;
import com.groove.payment.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 결제 요청 본문 (API.md §3.6 — POST /payments).
 *
 * <p>{@code orderNumber} 는 {@link OrderNumberFormat#PATTERN} 만 허용한다 — 형식 위반은 컨트롤러 진입
 * 단계에서 400 으로 거른다.
 *
 * @param orderNumber 결제 대상 주문의 외부 식별자
 * @param method      결제 수단
 */
public record PaymentCreateRequest(
        @Schema(description = "결제 대상 주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber,
        @Schema(description = "결제 수단", example = "CARD", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull PaymentMethod method) {
}
