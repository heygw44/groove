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
     * 키로 행을 일괄 삭제. 처리 실패 시 마커(소유자가 직접) 회수에 사용.
     *
     * @return 삭제된 행 수 (0 또는 1)
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey")
    int deleteByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 키로 만료된 {@code COMPLETED} 행만 삭제 (TTL 지난 캐시 회수).
     *
     * <p>비소유자가 stale read 기반으로 {@link #deleteByIdempotencyKey} 를 호출하면, 그 사이 다른
     * 스레드가 회수 후 새로 INSERT 한 {@code IN_PROGRESS} 마커까지 지워 action 이중 실행을 유발할 수 있다.
     * 따라서 {@code status = COMPLETED AND expiresAt <= now} 조건을 함께 걸어, 본 적 있는 만료 캐시 행이
     * 그대로 남아 있을 때만 삭제한다 — 그 사이 새 마커로 교체됐으면 0 행 삭제로 안전하게 빠진다.
     * 경계 비교는 {@link IdempotencyRecord#isExpired(Instant)}(now ≥ expiresAt) 와 동일하게 {@code <=} 다.
     *
     * @return 삭제된 행 수 (0 또는 1)
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.idempotencyKey = :idempotencyKey "
            + "AND r.status = com.groove.common.idempotency.IdempotencyStatus.COMPLETED "
            + "AND r.expiresAt <= :now")
    int deleteExpiredCompleted(@Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);

    /**
     * TTL 경과 행을 최대 {@code batchSize} 개 삭제 (TTL 정리, MySQL {@code DELETE … LIMIT}).
     *
     * <p>상태별로 회수 시점을 분리한다:
     * <ul>
     *   <li>{@code COMPLETED}: {@code expiresAt <= now} — 캐시 보관 기간(TTL)이 지나면 삭제.</li>
     *   <li>{@code IN_PROGRESS}: {@code expiresAt <= inProgressCutoff}({@code = now - grace}) — TTL 경과
     *       만으로 지우지 않고 grace 만큼 더 기다린다. 처리 중인 느린 action 의 마커가 삭제돼 2차 요청이
     *       마커 INSERT 에 성공 → action 이중 실행되는 것을 막는다(멱등성 핵심 계약). grace 가 최장 action
     *       소요보다 길면 진짜 멈춘 마커만 회수되고 처리 중 마커는 보존된다.</li>
     * </ul>
     *
     * <p>{@code inProgressCutoff < now} 가 항상 성립(grace 양수)하므로, 두 조건을 공통 범위
     * {@code expires_at <= now} 로 묶어 {@code idx_idempotency_expires} 단일 레인지 스캔 + status/grace
     * 잔여 필터로 처리한다(OR 두 갈래가 인덱스를 못 타는 것을 피한다). 경계는 {@code isExpired} 와 같은 {@code <=}.
     *
     * <p>{@code now}/{@code inProgressCutoff} 는 호출 시점 고정값이라 삭제 대상 집합은 유한하다 — 호출자가
     * 0 이 반환될 때까지 반복하며, 매 반복을 독립 트랜잭션으로 커밋해 한 번에 잡는 락 범위를 제한한다.
     *
     * @return 삭제된 행 수
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
