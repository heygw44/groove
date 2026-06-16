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
 * 멱등성 레코드 엔티티. 한 행이 처리 소유권 마커(IN_PROGRESS)와 결과 캐시(COMPLETED + responseBody)를 겸한다.
 *
 * <p>생성은 start 정적 팩토리만 허용하며 IN_PROGRESS·expiresAt = now + ttl 로 시작한다. 완료 전이는
 * complete 단일 진입점이고, 이미 COMPLETED 인 행에 다시 호출하면 IllegalStateException. idempotencyKey 에
 * DB UNIQUE 제약이 걸려 있다.
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

    /** 요청 페이로드 지문(호출자 정의). null 이면 replay 일치 검증을 건너뛴다. */
    @Column(name = "request_fingerprint", length = 255)
    private String requestFingerprint;

    /** 캐시된 결과의 타입 FQN. */
    @Column(name = "response_type", length = 255)
    private String responseType;

    /** 캐시된 결과의 JSON 직렬화. 결과가 null 이면 null. */
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

    /** 마커 행 생성. 상태 IN_PROGRESS, expiresAt = now + ttl. */
    public static IdempotencyRecord start(String idempotencyKey, String requestFingerprint, Duration ttl, Instant now) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        Objects.requireNonNull(now, "now must not be null");
        return new IdempotencyRecord(idempotencyKey, requestFingerprint, now.plus(ttl));
    }

    /** 처리 완료 + 결과 캐싱. IN_PROGRESS → COMPLETED 전이만 허용. */
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

    /** 저장된 지문과 주어진 지문이 둘 다 non-null 이고 다르면 true. 한 쪽이 null 이면 false. */
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
