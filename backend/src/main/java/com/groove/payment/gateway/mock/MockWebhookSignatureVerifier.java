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
 * Mock 웹훅 서명 검증 — 수신 서명이 공유 시크릿(payment.mock.webhook-secret)과 일치하는지
 * 상수 시간 비교(MessageDigest.isEqual)로 확인한다.
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
