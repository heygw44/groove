package com.groove.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 멱등성 인프라 설정 (groove.idempotency.*). compact constructor 에서 검증한다.
 *
 * - ttl: COMPLETED 레코드(결과 캐시) 보관 기간 (양수).
 * - inProgressTimeout: IN_PROGRESS 마커 처리 타임아웃 — 초과한 마커는 죽은 소유자가 남긴 것으로 보고
 *   회수한다. 반드시 정상 action 최대 소요(innodb_lock_wait_timeout~50s·PG read-timeout 5s 등)보다 충분히
 *   크게 잡는다 — 작으면 살아있는 마커가 동시 재시도/스케줄러에 회수돼 action 이 이중 실행된다. 기본 PT10M.
 * - cleanupBatchSize: 한 트랜잭션에서 삭제하는 최대 행 수 (1 이상).
 */
@ConfigurationProperties(prefix = "groove.idempotency")
public record IdempotencyProperties(
        @DefaultValue("PT24H") Duration ttl,
        @DefaultValue("PT10M") Duration inProgressTimeout,
        @DefaultValue("1000") int cleanupBatchSize
) {

    public IdempotencyProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("groove.idempotency.ttl 은 0보다 큰 Duration 이어야 합니다 (현재: " + ttl + ")");
        }
        if (inProgressTimeout == null || inProgressTimeout.isZero() || inProgressTimeout.isNegative()) {
            throw new IllegalStateException("groove.idempotency.in-progress-timeout 은 0보다 큰 Duration 이어야 합니다 (현재: " + inProgressTimeout + ")");
        }
        if (cleanupBatchSize <= 0) {
            throw new IllegalStateException("groove.idempotency.cleanup-batch-size 는 1 이상이어야 합니다 (현재: " + cleanupBatchSize + ")");
        }
    }
}
