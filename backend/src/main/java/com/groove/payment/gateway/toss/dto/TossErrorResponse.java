package com.groove.payment.gateway.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 에러 응답 바디. 진단 로깅 용도.
 * PaymentGatewayException 은 cause 만 받으므로 message 를 502 응답 본문에 노출하지 않고 로그/cause 체인으로만 남긴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(String code, String message) {
}
