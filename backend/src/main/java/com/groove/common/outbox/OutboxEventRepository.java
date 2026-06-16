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
 * <p>릴레이는 findByPublishedAtIsNullOrderByIdAsc 로 대상을 조회하고 markPublished 로 발행 완료를 표시한다.
 * 정리 스케줄러는 deletePublishedBefore 로 보관 기간이 지난 발행 완료 행을 회수한다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 미발행(published_at IS NULL) 행을 id 오름차순(FIFO)으로 최대 limit 개 조회한다. */
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    /** 발행 완료로 표시 (조건부 UPDATE, published_at IS NULL 가드). 갱신된 행 수(0 또는 1)를 반환한다. */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id = :id AND o.publishedAt IS NULL")
    int markPublished(@Param("id") Long id, @Param("now") Instant now);

    /** 보관 기간이 지난 발행 완료 행을 최대 batchSize 개 삭제하고 삭제된 행 수를 반환한다 (MySQL DELETE … LIMIT). */
    @Modifying
    @Query(value = "DELETE FROM outbox_event WHERE published_at IS NOT NULL AND published_at < :cutoff "
            + "LIMIT :batchSize", nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
