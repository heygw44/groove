package com.groove.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 멱등성 레코드 영속성.
 *
 * <p>마커 INSERT 는 {@link JpaRepository#saveAndFlush} 로 즉시 플러시해 UNIQUE 위반을
 * 호출 시점에 동기적으로 드러낸다 ({@link IdempotencyService} 참조).
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * 키로 행을 일괄 삭제. 처리 실패 시 마커 회수에 사용.
     *
     * @return 삭제된 행 수 (0 또는 1)
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey")
    int deleteByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * {@code expiresAt < now} 인 행을 최대 {@code batchSize} 개 삭제 (TTL 정리).
     *
     * <p>{@code now} 는 호출 시점 고정값이라 삭제 대상 집합은 유한하다 — 호출자가 0 이 반환될 때까지
     * 반복하며, 매 반복을 독립 트랜잭션으로 커밋해 한 번에 잡는 락 범위를 제한한다.
     *
     * @return 삭제된 행 수
     */
    @Modifying
    @Query(value = "DELETE FROM idempotency_record WHERE expires_at < :now LIMIT :batchSize", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
