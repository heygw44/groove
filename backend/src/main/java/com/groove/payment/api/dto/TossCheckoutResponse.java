package com.groove.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토스 결제 요청(checkout) 응답 — 프론트 결제위젯 초기화에 필요한 값.
 *
 * <p>clientKey 로 위젯을 띄우고, orderId(= 주문번호)·amount(= 서버 저장 payable)로 결제창을 연다.
 * successUrl 은 결제별 토큰(callback_token)이 쿼리에 박힌, failUrl 은 토큰 없는 서버 콜백 URL로, 위젯의 requestPayment 에 그대로 넘긴다(#309).
 * clientKey·successUrl·failUrl 은 dev/prod 에서만 바인딩되며(test/local 은 null) — 토스 모드는 dev/prod 한정이다.
 */
public record TossCheckoutResponse(
        @Schema(description = "토스 결제위젯 공개 클라이언트 키 (dev/prod 에서만 제공, 그 외 null)",
                example = "test_ck_xxxxxxxxxxxxxxxxxxxxxxxx")
        String clientKey,
        @Schema(description = "토스 orderId — 주문번호를 그대로 사용", example = "ORD-20260622-A1B2C3")
        String orderId,
        @Schema(description = "결제 예정액(payable) — 서버 저장값. confirm 시 위변조 검증 기준", example = "32000")
        long amount,
        @Schema(description = "결제 성공 콜백 URL — 결제별 토큰 쿼리 포함. 위젯 requestPayment 의 successUrl 로 사용 (dev/prod 만)",
                example = "http://localhost:8080/payments/toss/success?token=...")
        String successUrl,
        @Schema(description = "결제 실패 콜백 URL — 토큰 미부착(fail 은 상태 무변경이라 검증 불필요, #309). 위젯 requestPayment 의 failUrl 로 사용 (dev/prod 만)",
                example = "http://localhost:8080/payments/toss/fail")
        String failUrl) {
}
