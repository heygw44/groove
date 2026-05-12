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
 * <p>{@code cleanup-batch-size=2} 로 띄워 배치 루프(여러 반복)와 만료 조건(만료분만 삭제)을 함께 검증한다.
 * 스케줄러 자동 실행은 test 프로파일에서 {@code cleanup-cron: "-"} 로 꺼져 있으므로 태스크를 직접 호출한다.
 */
@SpringBootTest(properties = "groove.idempotency.cleanup-batch-size=2")
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
    @DisplayName("만료된 레코드만 배치로 삭제, 유효한 레코드는 보존")
    void deletesOnlyExpired_inBatches() {
        for (int i = 0; i < 5; i++) {
            saveWithExpiry(Instant.now().minus(Duration.ofHours(1)));
        }
        String fresh1 = saveWithExpiry(Instant.now().plus(Duration.ofHours(1)));
        String fresh2 = saveWithExpiry(Instant.now().plus(Duration.ofHours(24)));

        int deleted = cleanupTask.deleteExpired(Instant.now());

        assertThat(deleted).isEqualTo(5);
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByIdempotencyKey(fresh1)).isPresent();
        assertThat(repository.findByIdempotencyKey(fresh2)).isPresent();
    }

    @Test
    @DisplayName("만료된 레코드가 없으면 0건 — 아무것도 삭제하지 않음")
    void noExpired_deletesNothing() {
        saveWithExpiry(Instant.now().plus(Duration.ofHours(1)));

        int deleted = cleanupTask.deleteExpired(Instant.now());

        assertThat(deleted).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("cleanupExpired() — 스케줄 진입점도 예외 없이 정리 수행")
    void scheduledEntryPoint_cleansUp() {
        saveWithExpiry(Instant.now().minus(Duration.ofHours(1)));

        cleanupTask.cleanupExpired();

        assertThat(repository.count()).isZero();
    }

    private String saveWithExpiry(Instant expiresAt) {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord record = IdempotencyRecord.start(key, null, Duration.ofHours(24));
        ReflectionTestUtils.setField(record, "expiresAt", expiresAt);
        repository.saveAndFlush(record);
        return key;
    }
}
