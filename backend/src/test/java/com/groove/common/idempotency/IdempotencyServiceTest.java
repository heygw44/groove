package com.groove.common.idempotency;

import com.groove.common.idempotency.exception.IdempotencyConflictException;
import com.groove.common.idempotency.exception.IdempotencyKeyReuseMismatchException;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * IdempotencyService 통합 테스트 (Testcontainers MySQL).
 *
 * <p>마커 INSERT 의 UNIQUE 제약과 {@code REQUIRES_NEW} 트랜잭션 가시성이 핵심이라 실 DB 에서 검증한다.
 * 동시성 강검증(완전한 단일 처리 보장)은 #W11-1 에서 추가하며, 여기서는 기본 동작만 다룬다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("IdempotencyService — 멱등 실행 / 결과 캐싱 / 충돌")
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRecordRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    record SampleResult(String value, int n) {
    }

    @Test
    @DisplayName("최초 호출 — action 1회 실행 후 결과 캐싱, 재호출은 캐시 결과 반환")
    void firstCall_runsActionOnce_andCachesResult() {
        AtomicInteger counter = new AtomicInteger();
        String key = UUID.randomUUID().toString();

        SampleResult first = idempotencyService.execute(key, SampleResult.class, () -> {
            counter.incrementAndGet();
            return new SampleResult("hello", 7);
        });
        SampleResult second = idempotencyService.execute(key, SampleResult.class, () -> {
            counter.incrementAndGet();
            return new SampleResult("SHOULD-NOT-RUN", 99);
        });

        assertThat(counter.get()).isEqualTo(1);
        assertThat(first).isEqualTo(new SampleResult("hello", 7));
        assertThat(second).isEqualTo(first);

        IdempotencyRecord record = repository.findByIdempotencyKey(key).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResponseType()).isEqualTo(SampleResult.class.getName());
        assertThat(record.getResponseBody()).contains("hello");
    }

    @Test
    @DisplayName("null 결과도 캐싱 — 재호출 시 action 미실행")
    void nullResult_cached_actionNotRerun() {
        AtomicInteger counter = new AtomicInteger();
        String key = UUID.randomUUID().toString();

        SampleResult first = idempotencyService.execute(key, SampleResult.class, () -> {
            counter.incrementAndGet();
            return null;
        });
        SampleResult second = idempotencyService.execute(key, SampleResult.class, () -> {
            counter.incrementAndGet();
            return new SampleResult("x", 1);
        });

        assertThat(counter.get()).isEqualTo(1);
        assertThat(first).isNull();
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("처리 중(IN_PROGRESS) 마커가 있으면 동일 키 요청은 409, action 미실행")
    void inProgressMarker_concurrentRequest_throws409() {
        String key = UUID.randomUUID().toString();
        repository.saveAndFlush(IdempotencyRecord.start(key, null, Duration.ofHours(1)));
        AtomicInteger counter = new AtomicInteger();

        assertThatThrownBy(() -> idempotencyService.execute(key, SampleResult.class, () -> {
            counter.incrementAndGet();
            return new SampleResult("x", 1);
        })).isInstanceOf(IdempotencyConflictException.class);

        assertThat(counter.get()).isZero();
    }

    @Test
    @DisplayName("action 예외 — 마커 회수 후 동일 키 재시도 가능")
    void actionThrows_removesMarker_allowsRetry() {
        String key = UUID.randomUUID().toString();
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> idempotencyService.execute(key, SampleResult.class, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(repository.findByIdempotencyKey(key)).isEmpty();

        SampleResult result = idempotencyService.execute(key, SampleResult.class, () -> {
            attempts.incrementAndGet();
            return new SampleResult("ok", 1);
        });

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(result).isEqualTo(new SampleResult("ok", 1));
        assertThat(repository.findByIdempotencyKey(key).orElseThrow().getStatus())
                .isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    @DisplayName("처리 완료된 키를 다른 지문으로 재사용 — 409, 같은 지문이면 캐시 결과 반환")
    void completedKey_differentFingerprint_throws409() {
        String key = UUID.randomUUID().toString();
        idempotencyService.execute(key, "fp-A", SampleResult.class, () -> new SampleResult("v", 1));

        assertThatThrownBy(() -> idempotencyService.execute(key, "fp-B", SampleResult.class, () -> new SampleResult("v", 1)))
                .isInstanceOf(IdempotencyKeyReuseMismatchException.class);

        SampleResult cached = idempotencyService.execute(key, "fp-A", SampleResult.class, () -> new SampleResult("SHOULD-NOT-RUN", 9));
        assertThat(cached).isEqualTo(new SampleResult("v", 1));
    }

    @Test
    @DisplayName("blank 키 — IllegalArgumentException")
    void blankKey_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> idempotencyService.execute("   ", SampleResult.class, () -> null));
    }

    @Test
    @DisplayName("같은 키로 동시 다발 요청 — action 정확히 1회만 실행, 나머지는 캐시 결과 또는 409")
    void concurrentSameKey_actionRunsExactlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        AtomicInteger counter = new AtomicInteger();
        int threadCount = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return idempotencyService.execute(key, SampleResult.class, () -> {
                        counter.incrementAndGet();
                        sleepQuietly();
                        return new SampleResult("once", 1);
                    });
                } catch (RuntimeException e) {
                    return e;
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        int successes = 0;
        int conflicts = 0;
        for (Future<Object> future : futures) {
            Object outcome = future.get();
            if (outcome instanceof SampleResult sample) {
                assertThat(sample).isEqualTo(new SampleResult("once", 1));
                successes++;
            } else if (outcome instanceof IdempotencyConflictException) {
                conflicts++;
            } else {
                fail("예상치 못한 결과: " + outcome);
            }
        }

        assertThat(counter.get()).isEqualTo(1);
        assertThat(successes).isGreaterThanOrEqualTo(1);
        assertThat(successes + conflicts).isEqualTo(threadCount);
        assertThat(repository.findByIdempotencyKey(key).orElseThrow().getStatus())
                .isEqualTo(IdempotencyStatus.COMPLETED);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
