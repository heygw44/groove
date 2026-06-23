package com.groove.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토스 결제 요청(checkout) 응답 — 프론트 결제위젯 초기화에 필요한 값.
 *
 * <p>clientKey 로 위젯을 띄우고, orderId(= 주문번호)·amount(= 서버 저장 payable)로 결제창을 연다.
 * clientKey 는 dev/prod 에서만 바인딩되며(test/local 은 null), 위젯 배선은 후속 이슈다.
 */
public record TossCheckoutResponse(
        @Schema(description = "토스 결제위젯 공개 클라이언트 키 (dev/prod 에서만 제공, 그 외 null)",
                example = "test_ck_xxxxxxxxxxxxxxxxxxxxxxxx")
        String clientKey,
        @Schema(description = "토스 orderId — 주문번호를 그대로 사용", example = "ORD-20260622-A1B2C3")
        String orderId,
        @Schema(description = "결제 예정액(payable) — 서버 저장값. confirm 시 위변조 검증 기준", example = "32000")
        long amount) {
}
