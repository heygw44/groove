package com.groove.payment.gateway.mock;

import com.groove.payment.exception.InvalidWebhookSignatureException;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.WebhookSignatureVerifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Mock 웹훅 서명 검증 — 수신 서명이 공유 시크릿({@code payment.mock.webhook-secret})과 정확히 일치하는지
 * 확인한다. 타이밍 공격 회피를 위해 상수 시간 비교({@link MessageDigest#isEqual})를 쓴다 — Mock 이라 실익은
 * 작지만 실 PG 서명 검증 코드의 형태를 맞춰 둔다. {@code @Profile} 로 Mock 구성에 한정된다.
 */
@Component
@Profile({"local", "dev", "test", "docker"})
public class MockWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private final byte[] expectedSecret;

    public MockWebhookSignatureVerifier(PaymentMockProperties properties) {
        this.expectedSecret = Objects.requireNonNull(properties, "properties")
                .webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void verify(String signature) {
        if (signature == null
                || !MessageDigest.isEqual(expectedSecret, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException();
        }
    }
}
