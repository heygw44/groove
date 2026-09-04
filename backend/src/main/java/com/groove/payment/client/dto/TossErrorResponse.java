package com.groove.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스 에러 응답. 사용자에게 그대로 내보내지 않고 로그와 fail_reason 에만 남긴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(String code, String message) {
}
