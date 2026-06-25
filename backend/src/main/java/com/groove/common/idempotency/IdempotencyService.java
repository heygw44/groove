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
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 멱등성 실행 진입점.
 *
 * execute(key, …, action) 은 같은 키에 대해 action 을 정확히 한 번만 실행하고 결과를 캐싱한다 — 후속 요청은
 * 캐시 결과를 받고, 처리 중인 키의 요청은 409. 모든 레코드 접근은 짧은 REQUIRES_NEW 트랜잭션으로 수행한다.
 * action 또는 결과 캐싱(직렬화·complete())이 예외를 던지면 마커를 삭제하고 재던진다.
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
    private final Duration inProgressTimeout;

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
        this.inProgressTimeout = properties.inProgressTimeout();
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
            String ownerToken = UUID.randomUUID().toString();
            if (tryInsertMarker(idempotencyKey, requestFingerprint, ownerToken)) {
                return runAndCache(idempotencyKey, ownerToken, action);
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
            if (!record.isCompleted() && record.isExpired(now)) {
                // 처리 타임아웃이 지난 IN_PROGRESS — 죽은 소유자가 남긴 마커로 보고 조건부 삭제로 회수한 뒤 처음부터
                // 재처리한다(스케줄러 정리를 기다리지 않음). 삭제 실패(DB 오류)는 전파한다.
                // 트레이드오프: inProgressTimeout 을 정상 action 최대 소요보다 충분히 크게 잡아야(IdempotencyProperties 참고)
                // 살아있는 마커를 회수하지 않는다 — 너무 짧으면 진행 중 action 의 마커가 회수돼 이중 실행된다(#317).
                requiresNewTx.executeWithoutResult(status -> repository.deleteExpiredInProgress(idempotencyKey, now));
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

    private boolean tryInsertMarker(String idempotencyKey, String requestFingerprint, String ownerToken) {
        try {
            requiresNewTx.executeWithoutResult(status ->
                    repository.saveAndFlush(IdempotencyRecord.start(idempotencyKey, requestFingerprint, inProgressTimeout, clock.instant(), ownerToken)));
            return true;
        } catch (DataIntegrityViolationException duplicateKey) {
            return false;
        }
    }

    private <T> T runAndCache(String idempotencyKey, String ownerToken, Supplier<T> action) {
        T result;
        try {
            result = action.get();
        } catch (RuntimeException actionFailure) {
            removeMarkerQuietly(idempotencyKey, ownerToken);
            throw actionFailure;
        }

        try {
            String body = result == null ? null : objectMapper.writeValueAsString(result);
            String typeName = result == null ? null : result.getClass().getName();
            // 완료 시 expiresAt 을 처리 타임아웃에서 결과 캐시 보관 기간(now + ttl)으로 연장한다.
            Instant cacheExpiresAt = clock.instant().plus(ttl);
            requiresNewTx.executeWithoutResult(status -> {
                IdempotencyRecord record = repository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException("멱등성 마커가 사라졌습니다: " + idempotencyKey));
                // ownerToken 으로 소유권 검증 — 마커가 회수되고 다른 요청이 새로 만든 행이면 complete 가 거부된다(#317).
                record.complete(typeName, body, cacheExpiresAt, ownerToken);
            });
        } catch (RuntimeException cacheFailure) {
            // 결과 직렬화 또는 complete() 트랜잭션 실패 — action 실패 경로와 대칭으로 마커를 정리한다.
            removeMarkerQuietly(idempotencyKey, ownerToken);
            throw cacheFailure;
        }
        return result;
    }

    /**
     * 소유자가 처리 실패 시 자기 IN_PROGRESS 마커만 삭제한다. key+ownerToken+status=IN_PROGRESS 가드로,
     * 처리 타임아웃 초과로 마커가 회수돼 다른 요청이 같은 키로 만든 마커/COMPLETED 캐시는 파괴하지 않는다
     * (#317 회수 race 방어). 회수 실패는 경고만 남긴다.
     */
    private void removeMarkerQuietly(String idempotencyKey, String ownerToken) {
        try {
            requiresNewTx.executeWithoutResult(status -> repository.deleteInProgressByKeyAndOwner(idempotencyKey, ownerToken));
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
