package com.groove.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 멱등성 레코드 영속성. 마커 INSERT 는 saveAndFlush 로 즉시 플러시한다. */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * 키로 IN_PROGRESS 행만 삭제. 처리 실패 시 소유자가 자기 마커를 회수할 때 사용 — 회수 race 로
     * 다른 요청이 같은 키로 COMPLETED 캐시를 만든 경우 그 캐시 행은 건드리지 않는다(#317).
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey "
            + "AND r.status = com.groove.common.idempotency.IdempotencyStatus.IN_PROGRESS")
    int deleteInProgressByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 키로 status = COMPLETED AND expiresAt <= now 인 행만 삭제 (TTL 지난 캐시 회수). */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey "
            + "AND r.status = com.groove.common.idempotency.IdempotencyStatus.COMPLETED "
            + "AND r.expiresAt <= :now")
    int deleteExpiredCompleted(@Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);

    /** 키로 status = IN_PROGRESS AND expiresAt <= now 인 행만 삭제 (처리 타임아웃 지난 죽은 마커 회수). */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey "
            + "AND r.status = com.groove.common.idempotency.IdempotencyStatus.IN_PROGRESS "
            + "AND r.expiresAt <= :now")
    int deleteExpiredInProgress(@Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);

    /**
     * expiresAt 경과 행을 최대 batchSize 개 삭제 (MySQL DELETE … LIMIT). IN_PROGRESS 는 처리 타임아웃(짧게),
     * COMPLETED 는 ttl(길게)로 expiresAt 이 자연 분리되므로 status 구분 없이 expires_at <= now 단일 조건으로
     * idx_idempotency_expires 레인지 스캔만으로 회수한다.
     */
    @Modifying
    @Query(value = "DELETE FROM idempotency_record "
            + "WHERE expires_at <= :now "
            + "LIMIT :batchSize", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now,
                           @Param("batchSize") int batchSize);
}
