package com.groove.payment.gateway.mock;

import com.groove.payment.exception.InvalidWebhookSignatureException;
import com.groove.payment.gateway.PaymentMockProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MockWebhookSignatureVerifier 단위 테스트")
class MockWebhookSignatureVerifierTest {

    private final MockWebhookSignatureVerifier verifier = new MockWebhookSignatureVerifier(
            new PaymentMockProperties(1.0, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, "the-secret"));

    @Test
    @DisplayName("시크릿과 정확히 일치하면 통과한다")
    void verify_matchingSecret_ok() {
        assertThatCode(() -> verifier.verify("the-secret")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시크릿이 다르면 InvalidWebhookSignatureException")
    void verify_wrongSecret_throws() {
        assertThatThrownBy(() -> verifier.verify("nope")).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    @DisplayName("서명이 null 이면 InvalidWebhookSignatureException")
    void verify_nullSignature_throws() {
        assertThatThrownBy(() -> verifier.verify(null)).isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
