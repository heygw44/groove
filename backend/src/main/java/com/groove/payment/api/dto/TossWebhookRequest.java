package com.groove.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토스페이먼츠 웹훅 수신 본문(#296).
 *
 * 토스가 PAYMENT_STATUS_CHANGED 를 POST 로 통보한다. 본문은 신뢰하지 않고 "상태가 바뀌었다"는 핑으로만 취급 —
 * 위조 방지·상태 판정은 data.paymentKey 로 결제 조회를 재호출한 권위 상태로 한다. 나머지 필드는 모두 무시(ignoreUnknown).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossWebhookRequest(
        @Schema(description = "웹훅 이벤트 타입", example = "PAYMENT_STATUS_CHANGED")
        String eventType,
        @Schema(description = "상태가 변경된 Payment 객체")
        Data data) {

    /** 토스 Payment 객체 중 재조회 키로 쓰는 {@code paymentKey} 만 사용한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @Schema(description = "토스 결제 키 — 재조회 키", example = "tviva20260623123456ABCD")
            String paymentKey) {
    }
}
