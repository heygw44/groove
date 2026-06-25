package com.groove.common.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("IdempotencyRecord 도메인 — 팩토리/상태 전이/지문")
class IdempotencyRecordTest {

    @Test
    @DisplayName("start — IN_PROGRESS 로 시작, expiresAt = now + 처리 타임아웃")
    void start_initialState() {
        Instant before = Instant.now();

        IdempotencyRecord record = IdempotencyRecord.start("key-1", "fp", Duration.ofHours(24), Instant.now(), "owner-1");

        assertThat(record.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(record.getRequestFingerprint()).isEqualTo("fp");
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(record.isCompleted()).isFalse();
        assertThat(record.getResponseBody()).isNull();
        assertThat(record.getExpiresAt())
                .isBetween(before.plus(Duration.ofHours(24)), Instant.now().plus(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("start — fingerprint 는 null 허용")
    void start_nullFingerprint() {
        IdempotencyRecord record = IdempotencyRecord.start("key-1", null, Duration.ofHours(1), Instant.now(), "owner-1");
        assertThat(record.getRequestFingerprint()).isNull();
    }

    @Test
    @DisplayName("start — blank key 거부")
    void start_blankKey_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IdempotencyRecord.start("  ", null, Duration.ofHours(1), Instant.now(), "owner-1"));
    }

    @Test
    @DisplayName("start — 비양수 처리 타임아웃 거부")
    void start_nonPositiveTtl_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IdempotencyRecord.start("k", null, Duration.ZERO, Instant.now(), "owner-1"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IdempotencyRecord.start("k", null, Duration.ofSeconds(-1), Instant.now(), "owner-1"));
    }

    @Test
    @DisplayName("complete — IN_PROGRESS → COMPLETED, 결과 캐싱 + expiresAt 을 캐시 보관 기간으로 연장")
    void complete_cachesResult() {
        Instant now = Instant.now();
        IdempotencyRecord record = IdempotencyRecord.start("k", null, Duration.ofMinutes(5), now, "owner-1");
        Instant cacheExpiresAt = now.plus(Duration.ofHours(24));

        record.complete("com.example.Dto", "{\"a\":1}", cacheExpiresAt, "owner-1");

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.isCompleted()).isTrue();
        assertThat(record.getResponseType()).isEqualTo("com.example.Dto");
        assertThat(record.getResponseBody()).isEqualTo("{\"a\":1}");
        assertThat(record.getExpiresAt()).isEqualTo(cacheExpiresAt);
    }

    @Test
    @DisplayName("complete — 이미 완료된 레코드에 재호출 시 IllegalStateException")
    void complete_alreadyCompleted_rejected() {
        Instant now = Instant.now();
        IdempotencyRecord record = IdempotencyRecord.start("k", null, Duration.ofMinutes(5), now, "owner-1");
        record.complete(null, null, now.plus(Duration.ofHours(24)), "owner-1");

        assertThatIllegalStateException()
                .isThrownBy(() -> record.complete("x", "y", now.plus(Duration.ofHours(24)), "owner-1"));
    }

    @Test
    @DisplayName("complete — 다른 ownerToken(마커 회수됨)으로 호출 시 소유권 상실 IllegalStateException (#317)")
    void complete_wrongOwner_rejected() {
        Instant now = Instant.now();
        IdempotencyRecord record = IdempotencyRecord.start("k", null, Duration.ofMinutes(5), now, "owner-1");

        assertThatIllegalStateException()
                .isThrownBy(() -> record.complete("x", "y", now.plus(Duration.ofHours(24)), "owner-2"));
    }

    @Test
    @DisplayName("isExpired — expiresAt 기준 경계 포함")
    void isExpired_boundary() {
        IdempotencyRecord record = IdempotencyRecord.start("k", null, Duration.ofSeconds(10), Instant.now(), "owner-1");
        Instant expiresAt = record.getExpiresAt();

        assertThat(record.isExpired(expiresAt.minusSeconds(1))).isFalse();
        assertThat(record.isExpired(expiresAt)).isTrue();
        assertThat(record.isExpired(expiresAt.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("fingerprintMismatch — 둘 다 non-null 이고 다를 때만 true")
    void fingerprintMismatch_rules() {
        IdempotencyRecord withFp = IdempotencyRecord.start("k", "A", Duration.ofHours(1), Instant.now(), "owner-1");
        assertThat(withFp.fingerprintMismatch("A")).isFalse();
        assertThat(withFp.fingerprintMismatch("B")).isTrue();
        assertThat(withFp.fingerprintMismatch(null)).isFalse();

        IdempotencyRecord noFp = IdempotencyRecord.start("k", null, Duration.ofHours(1), Instant.now(), "owner-1");
        assertThat(noFp.fingerprintMismatch("B")).isFalse();
        assertThat(noFp.fingerprintMismatch(null)).isFalse();
    }
}
