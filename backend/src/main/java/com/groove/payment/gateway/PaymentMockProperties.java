package com.groove.payment.gateway;

import com.groove.common.config.SecretPlaceholderGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Mock PG 동작 파라미터. payment.mock.* 키와 매핑되며 compact constructor 에서 검증한다.
 *
 * <p>successRate: 결제 성공 비율(0.0~1.0). delayMin/delayMax: 호출 처리 지연 범위.
 * webhookDelayMin/webhookDelayMax: 웹훅 콜백 발사 지연 범위. webhookSecret: 웹훅 서명 값.
 */
@ConfigurationProperties(prefix = "payment.mock")
public record PaymentMockProperties(
        double successRate,
        Duration delayMin,
        Duration delayMax,
        Duration webhookDelayMin,
        Duration webhookDelayMax,
        String webhookSecret
) {

    public PaymentMockProperties {
        if (successRate < 0.0 || successRate > 1.0) {
            throw new IllegalStateException("payment.mock.success-rate 는 0.0~1.0 범위여야 합니다 (현재: " + successRate + ")");
        }
        requireNonNegative(delayMin, "payment.mock.delay-min");
        requireNonNegative(delayMax, "payment.mock.delay-max");
        requireOrdered(delayMin, delayMax, "payment.mock.delay-min", "payment.mock.delay-max");
        requireNonNegative(webhookDelayMin, "payment.mock.webhook-delay-min");
        requireNonNegative(webhookDelayMax, "payment.mock.webhook-delay-max");
        requireOrdered(webhookDelayMin, webhookDelayMax, "payment.mock.webhook-delay-min", "payment.mock.webhook-delay-max");
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("payment.mock.webhook-secret 은 비어 있을 수 없습니다");
        }
        SecretPlaceholderGuard.rejectPlaceholder("payment.mock.webhook-secret", webhookSecret);
    }

    private static void requireNonNegative(Duration value, String key) {
        if (value == null || value.isNegative()) {
            throw new IllegalStateException(key + " 는 0 이상의 Duration 이어야 합니다");
        }
    }

    private static void requireOrdered(Duration min, Duration max, String minKey, String maxKey) {
        if (min.compareTo(max) > 0) {
            throw new IllegalStateException(minKey + " 는 " + maxKey + " 보다 클 수 없습니다 (" + min + " > " + max + ")");
        }
    }
}
