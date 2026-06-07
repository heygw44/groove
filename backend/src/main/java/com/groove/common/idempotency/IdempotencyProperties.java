package com.groove.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 멱등성 인프라 설정 ({@code groove.idempotency.*}).
 *
 * <p>compact constructor 에서 검증하므로 잘못된 운영 설정은 빈 생성 시점에 즉시 실패한다.
 * 정리 스케줄러의 cron 은 {@code @Scheduled(cron = "${groove.idempotency.cleanup-cron:…}")} 로
 * 환경에서 직접 읽으므로 여기에는 두지 않는다 ({@link IdempotencyRecordCleanupTask}).
 *
 * @param ttl              멱등성 레코드 보관 기간 — 이 시간이 지난 COMPLETED 레코드는 정리 스케줄러가 삭제한다 (양수)
 * @param inProgressGrace  IN_PROGRESS 마커 회수 유예 — 마커는 {@code expiresAt} 이 이 시간만큼 더
 *                         지나야(즉 생성 후 {@code ttl + inProgressGrace}) 정리 대상이 된다. 처리 중인
 *                         느린 action 의 마커가 TTL 경과만으로 삭제돼 action 이 이중 실행되는 것을 막는다 —
 *                         최장 action 소요보다 충분히 길게 둔다 (양수, {@link IdempotencyRecordCleanupTask})
 * @param cleanupBatchSize 정리 스케줄러가 한 트랜잭션에서 삭제하는 최대 행 수 (1 이상)
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
