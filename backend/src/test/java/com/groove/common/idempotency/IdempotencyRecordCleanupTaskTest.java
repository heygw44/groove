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
 *
 * <p>{@code cleanup-batch-size=2} 로 띄워 배치 루프(여러 반복)를, {@code in-progress-grace=PT1H} 로 고정해
 * 상태별 회수 시점(COMPLETED 는 {@code expiresAt} 경과, IN_PROGRESS 는 거기에 grace 추가)을 함께 검증한다.
 * 스케줄러 자동 실행은 test 프로파일에서 {@code cleanup-cron: "-"} 로 꺼져 있으므로 태스크를 직접 호출한다.
 */
@SpringBootTest(properties = {
        "groove.idempotency.cleanup-batch-size=2",
        "groove.idempotency.in-progress-grace=PT1H"
})
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("IdempotencyRecordCleanupTask — TTL 경과 레코드 정리")
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
    @DisplayName("만료된 COMPLETED 와 grace 지난 IN_PROGRESS 만 배치로 삭제, 유효분은 보존")
    void deletesExpiredCompletedAndStuckInProgress_inBatches() {
        for (int i = 0; i < 4; i++) {
            saveCompleted(Instant.now().minus(Duration.ofHours(1)));   // 만료 캐시 → 삭제
        }
        saveInProgress(Instant.now().minus(Duration.ofHours(2)));       // grace(1h) 지난 멈춘 마커 → 삭제
        String freshCache = saveCompleted(Instant.now().plus(Duration.ofHours(1)));       // 유효 캐시 → 보존
        String inFlight = saveInProgress(Instant.now().minus(Duration.ofMinutes(30)));    // grace 안 처리 중 → 보존

        int deleted = cleanupTask.deleteExpired(Instant.now());

        assertThat(deleted).isEqualTo(5);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByIdempotencyKey(freshCache)).isPresent();
        assertThat(repository.findByIdempotencyKey(inFlight)).isPresent();
    }

    @Test
    @DisplayName("TTL 지났어도 grace 안의 IN_PROGRESS 마커는 보존 — 느린 action 이중 실행 방지(#160)")
    void inProgressWithinGrace_isPreserved() {
        String inFlight = saveInProgress(Instant.now().minus(Duration.ofMinutes(1)));

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
        IdempotencyRecord record = IdempotencyRecord.start(key, null, Duration.ofHours(24));
        record.complete("com.groove.Sample", "{}");
        ReflectionTestUtils.setField(record, "expiresAt", expiresAt);
        repository.saveAndFlush(record);
        return key;
    }

    private String saveInProgress(Instant expiresAt) {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord record = IdempotencyRecord.start(key, null, Duration.ofHours(24));
        ReflectionTestUtils.setField(record, "expiresAt", expiresAt);
        repository.saveAndFlush(record);
        return key;
    }
}
