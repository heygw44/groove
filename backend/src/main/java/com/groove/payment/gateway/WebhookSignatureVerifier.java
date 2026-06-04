package com.groove.payment.gateway;

/**
 * 결제 웹훅 서명 검증 포트.
 *
 * <p>{@code POST /api/v1/payments/webhook} 의 {@code X-Mock-Signature} 헤더와 {@code MockWebhookSimulator}
 * 가 보내는 {@link WebhookNotification#signature()} 를 검증한다. v1 은 Mock 구현
 * ({@code MockWebhookSignatureVerifier} — 공유 시크릿 단순 비교) 한 가지만 둔다 — 실 PG 도입 시
 * {@code @Profile("prod")} 구현체를 추가하면 호출부 변경 없이 교체된다.
 */
@FunctionalInterface
public interface WebhookSignatureVerifier {

    /**
     * 서명을 검증한다.
     *
     * @param signature 수신 서명 값 (헤더 누락 시 {@code null} 일 수 있음)
     * @throws com.groove.payment.exception.InvalidWebhookSignatureException 서명이 유효하지 않으면 (HTTP 401)
     */
    void verify(String signature);
}
