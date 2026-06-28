package com.groove.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스 외부 PG 호출의 재시도·서킷브레이커 파라미터. payment.toss.resilience.* 키와 매핑되며 compact constructor 에서
 * 기본값·검증을 적용한다. dev/prod 프로파일에서만 바인딩한다.
 * 재시도는 일시 장애(5xx·연결 실패)만 대상이다. 워커 점유·UX 보호로 maxAttempts·waitDuration 을 작게 둔다.
 * 읽기 타임아웃은 재시도하지 않는다(이미 read-timeout 을 소모해 재시도는 점유만 늘린다).
 * 서킷브레이커는 실패·느린 호출이 임계를 넘으면 OPEN 되어 후속 호출을 빠르게 실패시켜 워커 점유·장애 전파를 끊고, waitDuration 후 HALF_OPEN 탐색한다.
 */
@ConfigurationProperties(prefix = "payment.toss.resilience")
public record TossResilienceProperties(
        Integer retryMaxAttempts,
        Duration retryWaitDuration,
        Float cbFailureRateThreshold,
        Duration cbSlowCallDurationThreshold,
        Float cbSlowCallRateThreshold,
        Integer cbSlidingWindowSize,
        Integer cbMinimumNumberOfCalls,
        Duration cbWaitInOpenState
) {

    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 2;
    private static final Duration DEFAULT_RETRY_WAIT = Duration.ofMillis(200);
    private static final float DEFAULT_CB_FAILURE_RATE = 50.0f;
    private static final Duration DEFAULT_CB_SLOW_CALL_DURATION = Duration.ofSeconds(3);
    private static final float DEFAULT_CB_SLOW_CALL_RATE = 100.0f;
    private static final int DEFAULT_CB_WINDOW_SIZE = 20;
    private static final int DEFAULT_CB_MIN_CALLS = 10;
    private static final Duration DEFAULT_CB_WAIT_OPEN = Duration.ofSeconds(10);

    public TossResilienceProperties {
        retryMaxAttempts = requirePositiveOrDefault(retryMaxAttempts, DEFAULT_RETRY_MAX_ATTEMPTS, "payment.toss.resilience.retry-max-attempts");
        retryWaitDuration = requirePositiveOrDefault(retryWaitDuration, DEFAULT_RETRY_WAIT, "payment.toss.resilience.retry-wait-duration");
        cbFailureRateThreshold = requirePercentOrDefault(cbFailureRateThreshold, DEFAULT_CB_FAILURE_RATE, "payment.toss.resilience.cb-failure-rate-threshold");
        cbSlowCallDurationThreshold = requirePositiveOrDefault(cbSlowCallDurationThreshold, DEFAULT_CB_SLOW_CALL_DURATION, "payment.toss.resilience.cb-slow-call-duration-threshold");
        cbSlowCallRateThreshold = requirePercentOrDefault(cbSlowCallRateThreshold, DEFAULT_CB_SLOW_CALL_RATE, "payment.toss.resilience.cb-slow-call-rate-threshold");
        cbSlidingWindowSize = requirePositiveOrDefault(cbSlidingWindowSize, DEFAULT_CB_WINDOW_SIZE, "payment.toss.resilience.cb-sliding-window-size");
        cbMinimumNumberOfCalls = requirePositiveOrDefault(cbMinimumNumberOfCalls, DEFAULT_CB_MIN_CALLS, "payment.toss.resilience.cb-minimum-number-of-calls");
        cbWaitInOpenState = requirePositiveOrDefault(cbWaitInOpenState, DEFAULT_CB_WAIT_OPEN, "payment.toss.resilience.cb-wait-in-open-state");
    }

    private static int requirePositiveOrDefault(Integer value, int fallback, String key) {
        if (value == null) {
            return fallback;
        }
        if (value <= 0) {
            throw new IllegalStateException(key + " 는 양수여야 합니다 (현재: " + value + ")");
        }
        return value;
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

    private static float requirePercentOrDefault(Float value, float fallback, String key) {
        if (value == null) {
            return fallback;
        }
        if (value <= 0.0f || value > 100.0f) {
            throw new IllegalStateException(key + " 는 0 초과 100 이하 백분율이어야 합니다 (현재: " + value + ")");
        }
        return value;
    }
}
