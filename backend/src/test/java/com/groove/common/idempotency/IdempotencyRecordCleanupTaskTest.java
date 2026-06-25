package com.groove.common.idempotency;

import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdempotencyRecordCleanupTask 통합 테스트 (Testcontainers MySQL).
 * cleanup-batch-size=2 로 배치 루프를, expiresAt <= now 단일 기준 회수(status 무관)를 검증한다. 태스크를 직접 호출한다.
 * IN_PROGRESS 는 처리 타임아웃(짧게), COMPLETED 는 ttl(길게)로 expiresAt 이 분리되므로 단일 기준으로 충분하다.
 */
@SpringBootTest(properties = {
        "groove.idempotency.cleanup-batch-size=2"
})
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("IdempotencyRecordCleanupTask — expiresAt 경과 레코드 정리")
class IdempotencyRecordCleanupTaskTest {

    @Autowired
    private IdempotencyRecordCleanupTask cleanupTask;

    @Autowired
    private IdempotencyRecordRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("expiresAt 지난 COMPLETED·IN_PROGRESS 를 status 무관 배치로 삭제, 미래 expiresAt 은 보존")
    void deletesExpiredRecords_inBatches() {
        for (int i = 0; i < 4; i++) {
            saveCompleted(Instant.now().minus(Duration.ofHours(1)));   // 만료 캐시 → 삭제
        }
        saveInProgress(Instant.now().minus(Duration.ofHours(2)));       // 처리 타임아웃 지난 마커 → 삭제
        saveInProgress(Instant.now().minus(Duration.ofMinutes(30)));    // 처리 타임아웃 지난 마커 → 삭제
        String freshCache = saveCompleted(Instant.now().plus(Duration.ofHours(1)));       // 유효 캐시 → 보존
        String inFlight = saveInProgress(Instant.now().plus(Duration.ofMinutes(5)));      // 처리 중 마커 → 보존

        int deleted = cleanupTask.deleteExpired(Instant.now());

        assertThat(deleted).isEqualTo(6);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByIdempotencyKey(freshCache)).isPresent();
        assertThat(repository.findByIdempotencyKey(inFlight)).isPresent();
    }

    @Test
    @DisplayName("처리 타임아웃 안의 IN_PROGRESS 마커(미래 expiresAt)는 보존 — 진행 중 이중 실행 방지(#160)")
    void inProgressWithinTimeout_isPreserved() {
        String inFlight = saveInProgress(Instant.now().plus(Duration.ofMinutes(1)));

        int deleted = cleanupTask.deleteExpired(Instant.now());

        assertThat(deleted).isZero();
        assertThat(repository.findByIdempotencyKey(inFlight)).isPresent();
    }

    @Test
    @DisplayName("만료된 레코드가 없으면 0건 — 아무것도 삭제하지 않음")
    void noExpired_deletesNothing() {
        saveCompleted(Instant.now().plus(Duration.ofHours(1)));

        int deleted = cleanupTask.deleteExpired(Instant.now());

        assertThat(deleted).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("cleanupExpired() — 스케줄 진입점도 예외 없이 정리 수행")
    void scheduledEntryPoint_cleansUp() {
        saveCompleted(Instant.now().minus(Duration.ofHours(1)));

        cleanupTask.cleanupExpired();

        assertThat(repository.count()).isZero();
    }

    private String saveCompleted(Instant expiresAt) {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord record = IdempotencyRecord.start(key, null, Duration.ofMinutes(5), Instant.now());
        record.complete("com.groove.Sample", "{}", Instant.now().plus(Duration.ofHours(24)));
        ReflectionTestUtils.setField(record, "expiresAt", expiresAt);
        repository.saveAndFlush(record);
        return key;
    }

    private String saveInProgress(Instant expiresAt) {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord record = IdempotencyRecord.start(key, null, Duration.ofMinutes(5), Instant.now());
        ReflectionTestUtils.setField(record, "expiresAt", expiresAt);
        repository.saveAndFlush(record);
        return key;
    }
}
