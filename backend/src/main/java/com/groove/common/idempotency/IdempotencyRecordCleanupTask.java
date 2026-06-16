package com.groove.common.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.groove.common.transaction.CommonTransactionConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * TTL 경과 멱등성 레코드 정리.
 *
 * <p>groove.idempotency.cleanup-cron cron 으로 주기 실행되며(기본 매시 정각), TTL 경과 행을
 * cleanup-batch-size 개씩 독립 트랜잭션으로 삭제한다. cron 을 "-" 로 두면 비활성화된다.
 * COMPLETED 는 expiresAt 경과 시, IN_PROGRESS 는 거기에 in-progress-grace 를 더 기다린 뒤 회수한다.
 * 정리 실패는 잡아서 로깅만 한다.
 */
@Component
public class IdempotencyRecordCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyRecordCleanupTask.class);

    private final IdempotencyRecordRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final int batchSize;
    private final Duration inProgressGrace;

    public IdempotencyRecordCleanupTask(IdempotencyRecordRepository repository,
                                        @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                                        Clock clock,
                                        IdempotencyProperties properties) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
        this.batchSize = properties.cleanupBatchSize();
        this.inProgressGrace = properties.inProgressGrace();
    }

    @Scheduled(cron = "${groove.idempotency.cleanup-cron:0 0 * * * *}")
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
     * TTL 경과 레코드를 모두 삭제한다 — COMPLETED 는 expiresAt < cutoff, IN_PROGRESS 는
     * expiresAt < cutoff - inProgressGrace 기준. 배치 단위로 0 이 반환될 때까지 반복한다.
     */
    int deleteExpired(Instant cutoff) {
        Instant inProgressCutoff = cutoff.minus(inProgressGrace);
        int total = 0;
        int deleted;
        do {
            Integer batch = requiresNewTx.execute(status -> repository.deleteExpiredBatch(cutoff, inProgressCutoff, batchSize));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
