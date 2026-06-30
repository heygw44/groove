package com.groove.common.outbox;

import com.groove.common.transaction.CommonTransactionConfig;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 발행 완료 아웃박스 레코드 정리. groove.outbox.cleanup.cron 으로 주기 실행되며(기본 매시 정각), 발행 완료 후
 * .retention 이 지난 행을 .batch-size 개씩 독립 트랜잭션으로 삭제한다. 미발행 행은 삭제하지 않는다.
 * 정리 실패는 로깅만 하고 다음 주기에 재시도한다.
 */
@Component
public class OutboxEventCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventCleanupTask.class);

    private final OutboxEventRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final Duration retention;
    private final int batchSize;

    public OutboxEventCleanupTask(OutboxEventRepository repository,
                                  @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                                  Clock clock,
                                  @Value("${groove.outbox.cleanup.retention:P7D}") Duration retention,
                                  @Value("${groove.outbox.cleanup.batch-size:1000}") int batchSize) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
        this.retention = Objects.requireNonNull(retention, "retention");
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("groove.outbox.cleanup.retention 은 양수여야 합니다: " + retention);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.outbox.cleanup.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${groove.outbox.cleanup.cron:0 0 * * * *}")
    @SchedulerLock(name = "outboxCleanup",
            lockAtMostFor = "${groove.outbox.cleanup.lock-at-most-for:PT5M}",
            lockAtLeastFor = "${groove.outbox.cleanup.lock-at-least-for:PT30S}")
    public void cleanupPublished() {
        try {
            int deleted = deletePublished(clock.instant().minus(retention));
            if (deleted > 0) {
                log.info("발행 완료 아웃박스 레코드 {}건 정리", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("아웃박스 레코드 정리 실패 — 다음 주기에 재시도", e);
        }
    }

    /** published_at < cutoff 인 발행 완료 행을 배치 단위로 0 이 반환될 때까지 삭제하고 삭제된 총 행 수를 반환한다. */
    int deletePublished(Instant cutoff) {
        int total = 0;
        int deleted;
        do {
            Integer batch = requiresNewTx.execute(status -> repository.deletePublishedBefore(cutoff, batchSize));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
