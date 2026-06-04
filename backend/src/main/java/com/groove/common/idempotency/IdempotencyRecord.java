package com.groove.common.idempotency;

import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 멱등성 레코드 (#W7-2, ERD §7).
 *
 * <p>두 역할을 한 행에서 겸한다 — (1) 처리 소유권 마커({@link IdempotencyStatus#IN_PROGRESS}),
 * (2) 처리 완료 후 결과 캐시({@link IdempotencyStatus#COMPLETED} + {@code responseBody}).
 *
 * <p>생성은 {@link #start(String, String, Duration)} 정적 팩토리만 허용한다 — 항상 {@code IN_PROGRESS}
 * 로 시작하고 {@code expiresAt = now + ttl} 이다. 완료 전이는 {@link #complete(String, String)} 단일
 * 진입점이며, 이미 {@code COMPLETED} 인 행에 다시 호출하면 {@link IllegalStateException}.
 *
 * <p>{@code idempotencyKey} 에 DB UNIQUE 제약이 걸려 있어, 두 호출자가 거의 동시에 같은 키로
 * INSERT 하면 한 쪽만 성공한다 — 이게 단일 처리 보장의 1차 방어선이다.
 */
@Entity
@Table(
        name = "idempotency_record",
        indexes = {
                @Index(name = "idx_idempotency_expires", columnList = "expires_at")
        }
)
public class IdempotencyRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    /** 요청 페이로드 지문(호출자 정의, 권장 SHA-256 hex). 미제공 시 null — 그 경우 replay 일치 검증을 건너뛴다. */
    @Column(name = "request_fingerprint", length = 255)
    private String requestFingerprint;

    /** 캐시된 결과의 타입 FQN — 디버깅/관측용 메타. 역직렬화는 호출자가 넘긴 {@code resultType} 을 기준으로 한다. */
    @Column(name = "response_type", length = 255)
    private String responseType;

    /** 캐시된 결과의 JSON 직렬화. 결과가 {@code null} 이면 null. */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String idempotencyKey, String requestFingerprint, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.expiresAt = expiresAt;
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    /**
     * 마커 행 생성. 상태 {@code IN_PROGRESS}, {@code expiresAt = now + ttl}.
     *
     * @param idempotencyKey     클라이언트 제공 키 (blank 불가)
     * @param requestFingerprint 요청 지문 (nullable)
     * @param ttl                보관 기간 (양수)
     */
    public static IdempotencyRecord start(String idempotencyKey, String requestFingerprint, Duration ttl) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return new IdempotencyRecord(idempotencyKey, requestFingerprint, Instant.now().plus(ttl));
    }

    /**
     * 처리 완료 + 결과 캐싱. {@code IN_PROGRESS} → {@code COMPLETED} 전이만 허용.
     *
     * @param responseType 결과 타입 FQN (결과가 null 이면 null 가능)
     * @param responseBody 결과 JSON (결과가 null 이면 null)
     */
    public void complete(String responseType, String responseBody) {
        if (status != IdempotencyStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 완료된 멱등성 레코드입니다: " + idempotencyKey);
        }
        this.status = IdempotencyStatus.COMPLETED;
        this.responseType = responseType;
        this.responseBody = responseBody;
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now).isBefore(expiresAt);
    }

    /** 저장된 지문이 주어진 지문과 양립 불가한지 — 둘 다 non-null 이고 다르면 true. 한 쪽이 null 이면 false(검증 생략). */
    public boolean fingerprintMismatch(String otherFingerprint) {
        return requestFingerprint != null
                && otherFingerprint != null
                && !requestFingerprint.equals(otherFingerprint);
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
