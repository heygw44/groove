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
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 멱등성 레코드 엔티티. 한 행이 처리 소유권 마커(IN_PROGRESS)와 결과 캐시(COMPLETED + responseBody)를 겸한다.
 *
 * 생성은 start 팩토리만 허용(IN_PROGRESS·expiresAt = now + 처리 타임아웃). 완료 전이는 complete 단일 진입점이며
 * expiresAt 을 결과 캐시 보관 기간(now + ttl)으로 연장한다. COMPLETED 행에 재호출하면 IllegalStateException.
 * idempotencyKey 에 DB UNIQUE 제약.
 */
@Entity
@Table(
        name = "idempotency_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "idx_idempotency_expires", columnList = "expires_at")
        }
)
public class IdempotencyRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
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

    /** 마커 소유권 토큰(#317). 회수 race 에서 원소유자만 자기 마커를 finalize/삭제하도록 식별한다. */
    @Column(name = "owner_token", length = 36)
    private String ownerToken;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String idempotencyKey, String requestFingerprint, Instant expiresAt, String ownerToken) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.expiresAt = expiresAt;
        this.ownerToken = ownerToken;
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    /** 마커 행 생성. 상태 IN_PROGRESS, expiresAt = now + 처리 타임아웃(짧게), ownerToken = 호출자별 고유 토큰. */
    public static IdempotencyRecord start(String idempotencyKey, String requestFingerprint, Duration inProgressTimeout,
                                          Instant now, String ownerToken) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (inProgressTimeout == null || inProgressTimeout.isZero() || inProgressTimeout.isNegative()) {
            throw new IllegalArgumentException("inProgressTimeout must be positive");
        }
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(ownerToken, "ownerToken must not be null");
        return new IdempotencyRecord(idempotencyKey, requestFingerprint, now.plus(inProgressTimeout), ownerToken);
    }

    /**
     * 처리 완료 + 결과 캐싱. IN_PROGRESS → COMPLETED 전이만 허용하며 expiresAt 을 결과 캐시 보관 기간
     * (newExpiresAt = now + ttl)으로 연장한다. 레코드는 시계에 의존하지 않아 호출자가 계산해 넘긴다.
     * expectedOwnerToken 이 행의 ownerToken 과 다르면(타임아웃 초과로 회수돼 다른 요청이 새 마커 생성)
     * IllegalStateException — 원소유자가 남의 마커를 finalize 하지 못하게 한다(#317).
     */
    public void complete(String responseType, String responseBody, Instant newExpiresAt, String expectedOwnerToken) {
        if (status != IdempotencyStatus.IN_PROGRESS) {
            throw new IllegalStateException("이미 완료된 멱등성 레코드입니다: " + idempotencyKey);
        }
        if (!Objects.equals(ownerToken, expectedOwnerToken)) {
            throw new IllegalStateException("멱등성 마커 소유권을 상실했습니다(회수됨): " + idempotencyKey);
        }
        Objects.requireNonNull(newExpiresAt, "newExpiresAt must not be null");
        this.status = IdempotencyStatus.COMPLETED;
        this.responseType = responseType;
        this.responseBody = responseBody;
        this.expiresAt = newExpiresAt;
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

    public String getOwnerToken() {
        return ownerToken;
    }
}
