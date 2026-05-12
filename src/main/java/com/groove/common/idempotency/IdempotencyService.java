package com.groove.common.idempotency;

import com.groove.common.idempotency.exception.IdempotencyConflictException;
import com.groove.common.idempotency.exception.IdempotencyKeyReuseMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 멱등성 실행 진입점 (#W7-2).
 *
 * <p>{@code execute(key, …, action)} 는 같은 키에 대해 {@code action} 을 정확히 한 번만 실행하고 그
 * 결과를 캐싱한다 — 같은 키의 후속 요청은 캐시된 결과를 그대로 받고, 처리 중인 키의 요청은 409 다.
 *
 * <h2>동작</h2>
 * <ol>
 *   <li>{@code IN_PROGRESS} 마커 행을 독립 트랜잭션({@code REQUIRES_NEW})으로 INSERT — DB UNIQUE
 *       제약이 단일 처리 보장의 1차 방어선이다.</li>
 *   <li>INSERT 성공(소유권 획득) → {@code action} 실행 → 성공 시 결과를 JSON 으로 직렬화해
 *       독립 트랜잭션에서 마커를 {@code COMPLETED} 로 갱신하고 결과 반환. {@code action} 이 예외를
 *       던지면 마커 행을 삭제(재시도 허용)하고 그 예외를 재던진다.</li>
 *   <li>INSERT 실패(키 충돌) → 마커를 독립 트랜잭션으로 재조회:
 *       <ul>
 *         <li>{@code COMPLETED} → (지문 불일치면 {@link IdempotencyKeyReuseMismatchException}) 캐시 결과 반환.</li>
 *         <li>{@code IN_PROGRESS} → {@link IdempotencyConflictException} (409).</li>
 *         <li>마커가 사라졌으면(소유자 실패로 회수됨) 처음부터 다시 시도.</li>
 *       </ul></li>
 * </ol>
 *
 * <p>모든 레코드 접근은 호출자 트랜잭션과 무관한 짧은 {@code REQUIRES_NEW} 트랜잭션으로 수행한다 —
 * 마커는 {@code action} 실행 전에 커밋돼야 동시 호출자가 보고, 재조회는 매번 최신 스냅샷이어야 한다.
 *
 * <h2>호출 규약</h2>
 * <ul>
 *   <li>{@code execute()} 를 열린 트랜잭션 안에서 호출하지 말 것. {@code action} 자신이 트랜잭션을
 *       관리하고 반환 전에 커밋해야 한다(예: {@code @Transactional} 서비스 메서드를 {@code action} 으로,
 *       그 호출자는 비트랜잭션 컨트롤러). 그래야 "{@code action} 커밋 → 마커 {@code COMPLETED} 커밋"
 *       순서가 보장된다 — {@code action} 이 호출자 트랜잭션에 묶여 있으면 마커가 {@code COMPLETED} 로
 *       찍힌 뒤 그 트랜잭션이 롤백돼 (일어나지 않은 처리의 결과를) 캐시하게 될 수 있다.</li>
 * </ul>
 *
 * <h2>알려진 한계</h2>
 * <ul>
 *   <li>{@code action} 커밋 직후 완료 갱신 트랜잭션이 실패하면 해당 키는 TTL 만료 전까지 409 다 —
 *       {@link IdempotencyRecordCleanupTask} 가 회수한다. {@code action} 의 부수효과는 이미 반영됐으므로
 *       이 키로 재시도하면 안 된다(새 키를 써야 한다).</li>
 *   <li>{@code action} 의 결과는 JSON 왕복 가능한 단순 DTO 여야 한다 — 직렬화 불가 시
 *       {@code action} 부수효과 반영 후 예외가 전파되며 마커가 {@code IN_PROGRESS} 로 남는다(위와 동일).</li>
 *   <li>동시 동일 키 요청에 대한 강한 단일 처리 검증은 #W11-1 통합 테스트에서 추가한다.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** 마커 회수 race 가 끼어들 때 처음부터 재시도하는 최대 횟수. */
    private static final int MAX_ATTEMPTS = 3;

    private final IdempotencyRecordRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              @Qualifier(IdempotencyConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                              ObjectMapper objectMapper,
                              IdempotencyProperties properties) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.objectMapper = objectMapper;
        this.ttl = properties.ttl();
    }

    /**
     * 지문 없이 {@code action} 을 멱등 실행한다.
     *
     * @see #execute(String, String, Class, Supplier)
     */
    public <T> T execute(String idempotencyKey, Class<T> resultType, Supplier<T> action) {
        return execute(idempotencyKey, null, resultType, action);
    }

    /**
     * {@code action} 을 멱등 실행한다.
     *
     * @param idempotencyKey     클라이언트 제공 키 (blank 불가)
     * @param requestFingerprint 요청 페이로드 지문 (nullable — 같은 키 재사용 시 페이로드 변경 감지에만 쓰임)
     * @param resultType         {@code action} 결과 타입 — 캐시 replay 시 역직렬화에 사용
     * @param action             처리 본체 — 같은 키에 대해 한 번만 실행됨
     * @param <T>                결과 타입
     * @return {@code action} 의 결과, 또는 같은 키로 이미 처리된 경우 캐시된 결과
     * @throws IdempotencyConflictException          동일 키 요청이 처리 중인 경우 (409)
     * @throws IdempotencyKeyReuseMismatchException  이미 처리된 키가 다른 지문으로 재사용된 경우 (409)
     * @throws IllegalArgumentException              {@code idempotencyKey} 가 blank 인 경우
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
                    repository.saveAndFlush(IdempotencyRecord.start(idempotencyKey, requestFingerprint, ttl)));
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

    private void removeMarkerQuietly(String idempotencyKey) {
        try {
            requiresNewTx.executeWithoutResult(status -> repository.deleteByIdempotencyKey(idempotencyKey));
        } catch (RuntimeException cleanupFailure) {
            // 마커 회수 실패는 원래 예외 전파를 막지 않는다 — TTL 정리가 회수하므로 경고만 남긴다.
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
