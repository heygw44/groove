package com.groove.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Mock PG 동작 파라미터 (ARCHITECTURE.md §7.2).
 *
 * <p>{@code application.yaml} 의 {@code payment.mock.*} 키와 매핑된다. compact constructor 에서
 * 검증하므로 잘못된 운영 설정은 빈 생성 시점에 즉시 실패한다.
 *
 * @param successRate      결제 성공 비율 (0.0~1.0) — 웹훅이 {@code PAID} 로 발사될 확률
 * @param delayMin         {@code request/query/refund} 호출 처리 지연 하한 (음수 불가)
 * @param delayMax         처리 지연 상한 ({@code >= delayMin})
 * @param webhookDelayMin  웹훅 콜백 발사 지연 하한 (음수 불가)
 * @param webhookDelayMax  웹훅 콜백 발사 지연 상한 ({@code >= webhookDelayMin})
 * @param webhookSecret    웹훅 통보에 실어 보낼 서명 값 (검증은 #W7-4)
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
