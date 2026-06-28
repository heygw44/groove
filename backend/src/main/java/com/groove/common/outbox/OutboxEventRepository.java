package com.groove.common.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 아웃박스 영속성.
 *
 * 릴레이는 findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc 로 대상(미발행 + 재시도 상한 미만)을
 * 조회하고 markPublished 로 발행 완료를 표시한다. 핸들러 실패 시 incrementAttemptCount 로 카운터를 올려
 * 상한을 채운 poison 행을 릴레이 대상에서 제외(DLQ 격리)한다. 정리 스케줄러는 deletePublishedBefore 로
 * 보관 기간이 지난 발행 완료 행을 회수한다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 미발행(published_at IS NULL) 행을 id 오름차순(FIFO)으로 최대 limit 개 조회한다 (격리분 포함 — 관측/테스트용). */
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    /**
     * 릴레이 대상(미발행 + attempt_count < attemptCount)을 id 오름차순(FIFO)으로 최대 limit 개 조회한다.
     * attempt_count 가 임계값에 도달한 DLQ(격리) 행은 제외해 정상 이벤트 슬롯을 점유하지 않게 한다.
     */
    List<OutboxEvent> findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(int attemptCount, Limit limit);

    /**
     * DLQ(격리) 건수 — 미발행 + attempt_count >= attemptCount(=max-attempts) 인 행 수를 센다.
     * 격리 상태를 쿼리 가능한 형태로 노출하는 조건이며, 메트릭 Gauge 의 backing 으로 쓴다.
     * 릴레이 대상 조회와 대칭(LessThan 의 여집합)이고 idx_outbox_unpublished 범위 스캔으로 처리된다.
     */
    long countByPublishedAtIsNullAndAttemptCountGreaterThanEqual(int attemptCount);

    /**
     * DLQ(격리) 행을 id 오름차순(FIFO)으로 최대 limit 개 조회한다 — 운영 진단용.
     * 미발행 + attempt_count >= attemptCount(=max-attempts) 인, 수동 조치가 필요한 poison 행이다.
     */
    List<OutboxEvent> findByPublishedAtIsNullAndAttemptCountGreaterThanEqualOrderByIdAsc(int attemptCount, Limit limit);

    /** 발행 완료로 표시 (조건부 UPDATE, published_at IS NULL 가드). 갱신된 행 수(0 또는 1)를 반환한다. */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id = :id AND o.publishedAt IS NULL")
    int markPublished(@Param("id") Long id, @Param("now") Instant now);

    /** 핸들러 실패 시 재시도 카운터를 1 증가시킨다 (DLQ 임계 판정용, 독립 트랜잭션에서 호출). 갱신된 행 수를 반환한다. */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.attemptCount = o.attemptCount + 1 WHERE o.id = :id")
    int incrementAttemptCount(@Param("id") Long id);

    /** 보관 기간이 지난 발행 완료 행을 최대 batchSize 개 삭제하고 삭제된 행 수를 반환한다 (MySQL DELETE … LIMIT). */
    @Modifying
    @Query(value = "DELETE FROM outbox_event WHERE published_at IS NOT NULL AND published_at < :cutoff "
            + "LIMIT :batchSize", nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
