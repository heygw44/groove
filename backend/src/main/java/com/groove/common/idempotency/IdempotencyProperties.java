package com.groove.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 멱등성 인프라 설정 (groove.idempotency.*). compact constructor 에서 검증한다.
 *
 * <p>ttl: COMPLETED 레코드 보관 기간 (양수).
 * inProgressGrace: IN_PROGRESS 마커 회수 유예 — 마커는 생성 후 ttl + inProgressGrace 가 지나야 정리 대상 (양수).
 * cleanupBatchSize: 한 트랜잭션에서 삭제하는 최대 행 수 (1 이상).
 */
@ConfigurationProperties(prefix = "groove.idempotency")
public record IdempotencyProperties(
        @DefaultValue("PT24H") Duration ttl,
        @DefaultValue("PT1H") Duration inProgressGrace,
        @DefaultValue("1000") int cleanupBatchSize
) {

    public IdempotencyProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("groove.idempotency.ttl 은 0보다 큰 Duration 이어야 합니다 (현재: " + ttl + ")");
        }
        if (inProgressGrace == null || inProgressGrace.isZero() || inProgressGrace.isNegative()) {
            throw new IllegalStateException("groove.idempotency.in-progress-grace 는 0보다 큰 Duration 이어야 합니다 (현재: " + inProgressGrace + ")");
        }
        if (cleanupBatchSize <= 0) {
            throw new IllegalStateException("groove.idempotency.cleanup-batch-size 는 1 이상이어야 합니다 (현재: " + cleanupBatchSize + ")");
        }
    }
}
