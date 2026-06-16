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
 * 멱등성 실행 진입점.
 *
 * execute(key, …, action) 은 같은 키에 대해 action 을 정확히 한 번만 실행하고 결과를 캐싱한다 — 후속 요청은
 * 캐시 결과를 받고, 처리 중인 키의 요청은 409. 모든 레코드 접근은 짧은 REQUIRES_NEW 트랜잭션으로 수행한다.
 * action 이 예외를 던지면 마커를 삭제하고 재던진다.
 *
 * 호출 규약: execute() 를 열린 트랜잭션 안에서 호출하지 말 것. action 자신이 트랜잭션을 관리하고 반환 전에
 * 커밋해야 한다. action 결과는 JSON 왕복 가능한 단순 DTO 여야 한다.
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

    /** 지문 없이 action 을 멱등 실행한다. */
    public <T> T execute(String idempotencyKey, Class<T> resultType, Supplier<T> action) {
        return execute(idempotencyKey, null, resultType, action);
    }

    /**
     * action 을 멱등 실행한다. 같은 키로 이미 처리됐으면 캐시된 결과를 돌려준다.
     *
     * 동일 키 처리 중이면 IdempotencyConflictException(409), 이미 처리된 키가 다른 지문으로 재사용되면
     * IdempotencyKeyReuseMismatchException(409), idempotencyKey 가 blank 면 IllegalArgumentException.
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
                // 마커가 회수된 직후 — 처음부터 다시 시도한다.
                continue;
            }
            IdempotencyRecord record = existing.get();
            Instant now = clock.instant();
            if (record.isCompleted() && record.isExpired(now)) {
                // TTL 지난 캐시는 status=COMPLETED AND expiresAt<=now 조건부 삭제로 회수하고 처음부터 다시 처리.
                // 삭제 실패(DB 오류)는 전파한다.
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
        // 재시도 소진 — 충돌로 거부한다.
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

    /** 소유자가 처리 실패 시 자기 마커를 삭제한다. 회수 실패는 경고만 남긴다. */
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
