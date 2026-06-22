package com.groove.payment.gateway;

import com.groove.common.config.SecretPlaceholderGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 연동 파라미터(#293). payment.toss.* 키와 매핑되며 compact constructor 에서 검증한다.
 *
 * <p>dev/prod 프로파일에서만 바인딩된다(TossPaymentConfig 의 @Profile). test/local/docker 는
 * 기존 MockPaymentGateway 를 그대로 쓰므로 이 프로퍼티가 바인딩되지 않는다.
 *
 * <p>clientKey: 브라우저 결제위젯용 공개 키 — 노출 전제라 시크릿 가드 대상이 아니다.
 * secretKey: 서버 API 인증(Basic Auth)용 비밀 키 — jwt.secret 과 동일하게 플레이스홀더 기동을 거부한다.
 * connectTimeout/readTimeout: 토스 API 호출 타임아웃.
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
        // env 주입 값에 섞인 앞뒤 공백(개행 등)을 정규화 — Basic Auth 헤더 불일치(401)·URL 오동작 방지.
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
