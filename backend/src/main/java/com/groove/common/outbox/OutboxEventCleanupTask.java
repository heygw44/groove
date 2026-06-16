package com.groove.common.outbox;

import com.groove.common.transaction.CommonTransactionConfig;
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
 * 발행 완료 아웃박스 레코드 정리 (#237) — {@code IdempotencyRecordCleanupTask} 와 동일 골격.
 *
 * <p>{@code groove.outbox.cleanup.cron} cron 으로 주기 실행되며(기본 매시 정각), 발행 완료 후
 * {@code groove.outbox.cleanup.retention} 이 지난 행을 {@code .batch-size} 개씩 독립 트랜잭션으로 삭제한다 —
 * 한 번에 잡는 락 범위를 제한하기 위함이다. cron 을 {@code "-"} 로 두면 비활성화된다(테스트 프로파일에서 사용).
 *
 * <p>미발행 행은 삭제하지 않는다(릴레이가 발행해야 하므로). 정리 실패는 스케줄러 스레드 밖으로 흘리지 않고 로깅만
 * 한다 — 다음 주기에 재시도된다.
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

    /**
     * {@code published_at < cutoff} 인 발행 완료 행을 모두 삭제한다. {@code cutoff} 고정값 기준이라 대상 집합은
     * 유한하며, 배치 단위로 0 이 반환될 때까지 반복한다.
     *
     * @return 삭제된 총 행 수
     */
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
