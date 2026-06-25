package com.groove.common.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * runAndCache() 의 결과 캐싱 단계(직렬화 / complete() 트랜잭션) 실패 시 IN_PROGRESS 마커가 정리되는지
 * 검증하는 단위 테스트 (이슈 #267 — 정리 경로 대칭화).
 *
 * <p>실패 주입을 위해 실제 TransactionTemplate + mock PlatformTransactionManager 로 executeWithoutResult
 * 콜백을 실제 실행시키고, ObjectMapper / repository 를 mock 으로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService — 결과 캐싱 실패 시 마커 정리")
class IdempotencyServiceMarkerCleanupTest {

    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");

    @Mock
    private IdempotencyRecordRepository repository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private IdempotencyService idempotencyService;

    record SampleResult(String value, int n) {
    }

    @BeforeEach
    void setUp() {
        // 실제 TransactionTemplate + mock TM 으로 executeWithoutResult 콜백(마커 삽입·complete·정리)이 실행되게 한다.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate requiresNewTx = new TransactionTemplate(transactionManager);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        IdempotencyProperties properties =
                new IdempotencyProperties(Duration.ofHours(24), Duration.ofMinutes(5), 1000);
        idempotencyService = new IdempotencyService(repository, requiresNewTx, objectMapper, clock, properties);
    }

    @Test
    @DisplayName("결과 직렬화 실패 — 마커를 회수하고 예외를 재던진다")
    void serializationFailure_removesMarker() {
        String key = UUID.randomUUID().toString();
        RuntimeException serializeFailure = new RuntimeException("serialize boom");
        when(objectMapper.writeValueAsString(any())).thenThrow(serializeFailure);

        assertThatThrownBy(() ->
                idempotencyService.execute(key, SampleResult.class, () -> new SampleResult("ok", 1)))
                .isSameAs(serializeFailure);

        verify(repository).deleteInProgressByKeyAndOwner(eq(key), anyString());
    }

    @Test
    @DisplayName("complete() 트랜잭션 실패 — 마커를 회수하고 예외를 재던진다")
    void completeFailure_removesMarker() {
        String key = UUID.randomUUID().toString();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"value\":\"ok\",\"n\":1}");
        // complete() 블록 안에서 마커 조회 실패 → IllegalStateException 발생 → 캐싱 catch 경로로 진입.
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                idempotencyService.execute(key, SampleResult.class, () -> new SampleResult("ok", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("멱등성 마커가 사라졌습니다");

        verify(repository).deleteInProgressByKeyAndOwner(eq(key), anyString());
    }
}
