package com.groove.payment.api.dto;

import com.groove.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 결제 요청 본문 (API.md §3.6 — POST /payments).
 *
 * <p>{@code orderNumber} 는 {@code RandomOrderNumberGenerator} 형식({@code ORD-YYYYMMDD-XXXXXX},
 * 대문자/숫자 6자)만 허용한다 — 형식 위반은 컨트롤러 진입 단계에서 400 으로 거른다.
 *
 * @param orderNumber 결제 대상 주문의 외부 식별자
 * @param method      결제 수단
 */
public record PaymentCreateRequest(
        @NotBlank @Pattern(regexp = "^ORD-\\d{8}-[A-Z0-9]{6}$") String orderNumber,
        @NotNull PaymentMethod method) {
}
