package com.groove.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토스페이먼츠 웹훅 수신 본문(#296).
 *
 * <p>토스는 결제 상태 변경 시 {@code PAYMENT_STATUS_CHANGED} 이벤트를 POST 로 통보한다. 본문은 신뢰하지 않고
 * "상태가 바뀌었다"는 핑으로만 취급한다 — 실제 위조 방지·상태 판정은 {@code data.paymentKey} 로 결제 조회 API 를
 * 재호출(재조회 검증)해 토스가 직접 알려준 권위 상태로 한다. 그래서 본문의 다른 필드는 logic 에 쓰지 않으며,
 * 토스 페이로드의 나머지 필드는 모두 무시한다({@code ignoreUnknown}).
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
