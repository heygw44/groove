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
 * <p>릴레이는 {@link #findByPublishedAtIsNullOrderByIdAsc}(미발행 FIFO 배치) 로 대상을 조회하고,
 * 디스패치 성공 시 {@link #markPublished}(조건부 UPDATE) 로 발행 완료를 표시한다. 정리 스케줄러는
 * {@link #deletePublishedBefore} 로 보관 기간이 지난 발행 완료 행을 회수한다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 미발행({@code published_at IS NULL}) 행을 id 오름차순(FIFO)으로 최대 {@code limit} 개 — 릴레이 배치 조회. */
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    /**
     * 발행 완료로 표시 (조건부 UPDATE). {@code published_at IS NULL} 가드로 이미 발행된 행을 다시 찍지 않으며,
     * 그 자체가 동시 릴레이(수평 확장 시)의 이중 발행을 줄이는 1차 방어선이다 — 단, v1 은 단일 인스턴스를
     * 가정하므로 강한 중복 차단(SELECT … FOR UPDATE SKIP LOCKED)은 도입하지 않는다(컨슈머 멱등으로 흡수).
     *
     * @return 갱신된 행 수 (0 또는 1)
     */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.publishedAt = :now WHERE o.id = :id AND o.publishedAt IS NULL")
    int markPublished(@Param("id") Long id, @Param("now") Instant now);

    /**
     * 보관 기간이 지난 발행 완료 행을 최대 {@code batchSize} 개 삭제 (TTL 정리, MySQL {@code DELETE … LIMIT}).
     * {@code cutoff} 고정값 기준이라 대상 집합은 유한하며, 호출자가 0 이 반환될 때까지 반복한다.
     *
     * @return 삭제된 행 수
     */
    @Modifying
    @Query(value = "DELETE FROM outbox_event WHERE published_at IS NOT NULL AND published_at < :cutoff "
            + "LIMIT :batchSize", nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
