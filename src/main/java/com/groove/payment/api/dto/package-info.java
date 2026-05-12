/**
 * 결제 API 요청/응답 DTO (record). 응답 DTO 는 {@code IdempotencyService} 의 캐시 replay 대상이라
 * JSON 왕복 가능해야 한다. 도메인 변환은 응답 측만 수행하고, 요청 → 도메인 변환은
 * {@code PaymentService} 가 담당한다.
 */
package com.groove.payment.api.dto;
