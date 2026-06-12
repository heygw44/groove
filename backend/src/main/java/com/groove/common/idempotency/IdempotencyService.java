package com.groove.common.idempotency;

import com.groove.common.idempotency.exception.IdempotencyConflictException;
import com.groove.common.idempotency.exception.IdempotencyKeyReuseMismatchException;
import com.groove.common.transaction.CommonTransactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 멱등성 실행 진입점 (#W7-2).
 *
 * execute(key, …, action) 은 같은 키에 대해 action 을 정확히 한 번만 실행하고 결과를 캐싱한다 — 후속 요청은
 * 캐시 결과를 받고, 처리 중인 키의 요청은 409. 모든 레코드 접근은 호출자와 무관한 짧은 REQUIRES_NEW
 * 트랜잭션으로 수행한다 — IN_PROGRESS 마커가 action 실행 전에 커밋돼야 동시 호출자가 보고, DB UNIQUE 제약이
 * 단일 처리 보장의 1차 방어선이다. action 이 예외를 던지면 마커를 삭제(재시도 허용)하고 재던진다.
 *
 * 호출 규약: execute() 를 열린 트랜잭션 안에서 호출하지 말 것. action 자신이 트랜잭션을 관리하고 반환 전에
 * 커밋해야 "action 커밋 → 마커 COMPLETED 커밋" 순서가 보장된다 — action 이 호출자 트랜잭션에 묶이면 마커가
 * COMPLETED 로 찍힌 뒤 롤백돼 일어나지 않은 처리의 결과를 캐시할 수 있다. action 결과는 JSON 왕복 가능한 단순 DTO 여야 한다.
 *
 * 알려진 한계: action 커밋 직후 완료 갱신이 실패하면 키가 IN_PROGRESS 로 ttl + in-progress-grace 동안 409 다 —
 * IdempotencyRecordCleanupTask 가 그 후 회수한다. 부수효과는 이미 반영됐으므로 이 키로 재시도하지 말고 새 키를 써야
 * 한다. 상세 ARCHITECTURE.md §12 #4. (강한 단일 처리 검증은 #W11-1 통합 테스트.)
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** 마커 회수 race 가 끼어들 때 처음부터 재시도하는 최대 횟수. */
    private static final int MAX_ATTEMPTS = 3;

    private final IdempotencyRecordRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                              ObjectMapper objectMapper,
                              Clock clock,
                              IdempotencyProperties properties) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ttl = properties.ttl();
    }

    /**
     * 지문 없이 action 을 멱등 실행한다.
     *
     * @see #execute(String, String, Class, Supplier)
     */
    public <T> T execute(String idempotencyKey, Class<T> resultType, Supplier<T> action) {
        return execute(idempotencyKey, null, resultType, action);
    }

    /**
     * action 을 멱등 실행한다.
     *
     * @param idempotencyKey     클라이언트 제공 키 (blank 불가)
     * @param requestFingerprint 요청 페이로드 지문 (nullable — 같은 키 재사용 시 페이로드 변경 감지에만 쓰임)
     * @param resultType         action 결과 타입 — 캐시 replay 시 역직렬화에 사용
     * @param action             처리 본체 — 같은 키에 대해 한 번만 실행됨
     * @return action 의 결과, 또는 같은 키로 이미 처리된 경우 캐시된 결과
     * @throws IdempotencyConflictException          동일 키 요청이 처리 중인 경우 (409)
     * @throws IdempotencyKeyReuseMismatchException  이미 처리된 키가 다른 지문으로 재사용된 경우 (409)
     * @throws IllegalArgumentException              idempotencyKey 가 blank 인 경우
     */
    public <T> T execute(String idempotencyKey, String requestFingerprint, Class<T> resultType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        Objects.requireNonNull(resultType, "resultType must not be null");
        Objects.requireNonNull(action, "action must not be null");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (tryInsertMarker(idempotencyKey, requestFingerprint)) {
                return runAndCache(idempotencyKey, action);
            }
            Optional<IdempotencyRecord> existing = findInNewTx(idempotencyKey);
            if (existing.isEmpty()) {
                // 소유자가 처리 실패로 마커를 회수한 직후 — 처음부터 다시 시도한다.
                continue;
            }
            IdempotencyRecord record = existing.get();
            Instant now = clock.instant();
            if (record.isCompleted() && record.isExpired(now)) {
                // TTL 지난 캐시는 없는 것으로 간주한다("TTL 후엔 새 처리") — 만료 행을 회수하고 처음부터 다시 처리.
                // status=COMPLETED AND expiresAt<=now 조건부 삭제: stale read 로 다른 스레드가 이미 새 마커로
                // 교체했으면 0 행 삭제로 빠지고(정상) 다음 시도에서 재조회한다. 삭제 자체가 실패(DB 오류)하면
                // 삼키지 않고 전파해 500 으로 끝낸다 — 경쟁 요청이 없는데 재시도 소진 끝에 409 로 호도하지 않기 위함.
                requiresNewTx.executeWithoutResult(status -> repository.deleteExpiredCompleted(idempotencyKey, now));
                continue;
            }
            if (record.fingerprintMismatch(requestFingerprint)) {
                throw new IdempotencyKeyReuseMismatchException(idempotencyKey);
            }
            if (record.isCompleted()) {
                return deserialize(record.getResponseBody(), resultType);
            }
            throw new IdempotencyConflictException(idempotencyKey);
        }
        // 회수 race 가 연속으로 끼어든 극단 케이스 — 충돌로 거부한다(클라이언트 재시도).
        throw new IdempotencyConflictException(idempotencyKey);
    }

    private boolean tryInsertMarker(String idempotencyKey, String requestFingerprint) {
        try {
            requiresNewTx.executeWithoutResult(status ->
                    repository.saveAndFlush(IdempotencyRecord.start(idempotencyKey, requestFingerprint, ttl, clock.instant())));
            return true;
        } catch (DataIntegrityViolationException duplicateKey) {
            return false;
        }
    }

    private <T> T runAndCache(String idempotencyKey, Supplier<T> action) {
        T result;
        try {
            result = action.get();
        } catch (RuntimeException actionFailure) {
            removeMarkerQuietly(idempotencyKey);
            throw actionFailure;
        }

        String body = result == null ? null : objectMapper.writeValueAsString(result);
        String typeName = result == null ? null : result.getClass().getName();
        requiresNewTx.executeWithoutResult(status -> {
            IdempotencyRecord record = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("멱등성 마커가 사라졌습니다: " + idempotencyKey));
            record.complete(typeName, body);
        });
        return result;
    }

    /**
     * 소유자가 처리 실패 시 자기 마커를 회수한다(무조건 삭제 — 소유자만 호출하므로 race 무관).
     * 회수 실패는 best-effort — 곧 재던질 원래 action 예외를 가리지 않도록 경고만 남긴다(TTL 정리가 회수).
     */
    private void removeMarkerQuietly(String idempotencyKey) {
        try {
            requiresNewTx.executeWithoutResult(status -> repository.deleteByIdempotencyKey(idempotencyKey));
        } catch (RuntimeException cleanupFailure) {
            log.warn("멱등성 마커 회수 실패 key={} — TTL 정리로 회수 예정", idempotencyKey, cleanupFailure);
        }
    }

    private Optional<IdempotencyRecord> findInNewTx(String idempotencyKey) {
        return requiresNewTx.execute(status -> repository.findByIdempotencyKey(idempotencyKey));
    }

    private <T> T deserialize(String body, Class<T> resultType) {
        return body == null ? null : objectMapper.readValue(body, resultType);
    }
}
