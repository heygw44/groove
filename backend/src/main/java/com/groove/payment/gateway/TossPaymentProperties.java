package com.groove.payment.gateway;

import com.groove.common.config.SecretPlaceholderGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 연동 파라미터. payment.toss.* 키와 매핑되며 compact constructor 에서 검증한다.
 * dev/prod 프로파일에서만 바인딩한다(그 외는 MockPaymentGateway).
 * clientKey 는 위젯용 공개 키라 시크릿 가드를 제외한다. secretKey 는 Basic Auth 비밀 키라 플레이스홀더면 기동을 거부한다.
 */
@ConfigurationProperties(prefix = "payment.toss")
public record TossPaymentProperties(
        String baseUrl,
        String clientKey,
        String secretKey,
        String successUrl,
        String failUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final String DEFAULT_BASE_URL = "https://api.tosspayments.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    public TossPaymentProperties {
        // env 주입 값의 앞뒤 공백(개행 등)을 정규화한다(Basic Auth 헤더 불일치(401)·URL 오동작 방지).
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.strip();
        clientKey = clientKey == null ? null : clientKey.strip();
        secretKey = secretKey == null ? null : secretKey.strip();
        successUrl = successUrl == null ? null : successUrl.strip();
        failUrl = failUrl == null ? null : failUrl.strip();
        requireNonBlank(clientKey, "payment.toss.client-key");
        requireNonBlank(secretKey, "payment.toss.secret-key");
        SecretPlaceholderGuard.rejectPlaceholder("payment.toss.secret-key", secretKey);
        requireNonBlank(successUrl, "payment.toss.success-url");
        requireNonBlank(failUrl, "payment.toss.fail-url");
        connectTimeout = requirePositiveOrDefault(connectTimeout, DEFAULT_CONNECT_TIMEOUT, "payment.toss.connect-timeout");
        readTimeout = requirePositiveOrDefault(readTimeout, DEFAULT_READ_TIMEOUT, "payment.toss.read-timeout");
    }

    private static void requireNonBlank(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 값은 비어 있을 수 없습니다");
        }
    }

    private static Duration requirePositiveOrDefault(Duration value, Duration fallback, String key) {
        if (value == null) {
            return fallback;
        }
        if (value.isNegative() || value.isZero()) {
            throw new IllegalStateException(key + " 는 양수 Duration 이어야 합니다 (현재: " + value + ")");
        }
        return value;
    }
}
