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

    /** 키로 행을 삭제. 처리 실패 시 소유자가 마커를 회수할 때 사용. */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey")
    int deleteByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 키로 status = COMPLETED AND expiresAt <= now 인 행만 삭제 (TTL 지난 캐시 회수). */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey "
            + "AND r.status = com.groove.common.idempotency.IdempotencyStatus.COMPLETED "
            + "AND r.expiresAt <= :now")
    int deleteExpiredCompleted(@Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);

    /**
     * TTL 경과 행을 최대 batchSize 개 삭제 (MySQL DELETE … LIMIT). 상태별 회수 시점:
     * COMPLETED 는 expiresAt <= now, IN_PROGRESS 는 expiresAt <= inProgressCutoff (= now - grace).
     * 두 조건을 공통 범위 expires_at <= now 로 묶어 idx_idempotency_expires 단일 레인지 스캔 + status/grace 잔여 필터로 처리한다.
     */
    @Modifying
    @Query(value = "DELETE FROM idempotency_record "
            + "WHERE expires_at <= :now "
            + "AND (status = 'COMPLETED' OR expires_at <= :inProgressCutoff) "
            + "LIMIT :batchSize", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now,
                           @Param("inProgressCutoff") Instant inProgressCutoff,
                           @Param("batchSize") int batchSize);
}
