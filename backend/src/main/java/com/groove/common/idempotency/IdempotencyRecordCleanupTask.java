package com.groove.common.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.groove.common.transaction.CommonTransactionConfig;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * expiresAt 경과 멱등성 레코드 정리. groove.idempotency.cleanup-cron 으로 주기 실행되며(기본 매시 정각),
 * 경과 행을 cleanup-batch-size 개씩 독립 트랜잭션으로 삭제한다. IN_PROGRESS(처리 타임아웃)·COMPLETED(ttl)로
 * expiresAt 이 분리되므로 단일 기준으로 회수한다. 타임아웃 지난 IN_PROGRESS 는 다음 동일 키 요청이 즉시
 * 회수하므로(IdempotencyService) 이 스케줄러는 안전망이다. 정리 실패는 로깅만 한다.
 */
@Component
public class IdempotencyRecordCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordCleanupTask.class);

    private final IdempotencyRecordRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final int batchSize;

    public IdempotencyRecordCleanupTask(IdempotencyRecordRepository repository,
                                        @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                                        Clock clock,
                                        IdempotencyProperties properties) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
        this.batchSize = properties.cleanupBatchSize();
    }

    @Scheduled(cron = "${groove.idempotency.cleanup-cron:0 0 * * * *}")
    @SchedulerLock(name = "idempotencyCleanup",
            lockAtMostFor = "${groove.idempotency.cleanup-lock-at-most-for:PT5M}",
            lockAtLeastFor = "${groove.idempotency.cleanup-lock-at-least-for:PT30S}")
    public void cleanupExpired() {
        try {
            int deleted = deleteExpired(clock.instant());
            if (deleted > 0) {
                log.info("만료된 멱등성 레코드 {}건 정리", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("멱등성 레코드 정리 실패 — 다음 주기에 재시도", e);
        }
    }

    /**
     * expiresAt <= cutoff 인 레코드를 모두 삭제한다 (status 무관). 배치 단위로 0 이 반환될 때까지 반복한다.
     */
    int deleteExpired(Instant cutoff) {
        int total = 0;
        int deleted;
        do {
            Integer batch = requiresNewTx.execute(status -> repository.deleteExpiredBatch(cutoff, batchSize));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
