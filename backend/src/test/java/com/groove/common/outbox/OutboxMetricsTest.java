package com.groove.common.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 아웃박스 DLQ 메트릭 단위 테스트 — Gauge 가 repository 격리 카운트를 반영하고, Counter 가 eventType 별로 누적되는지.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxMetrics")
class OutboxMetricsTest {

    private static final int MAX_ATTEMPTS = 5;

    @Mock
    private OutboxEventRepository repository;

    private SimpleMeterRegistry registry;
    private OutboxMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new OutboxMetrics(registry, repository, MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("groove.outbox.dlq.size Gauge 는 scrape 시점의 repository 격리 카운트를 반영한다")
    void dlqSizeGauge_reflectsRepositoryCount() {
        given(repository.countByPublishedAtIsNullAndAttemptCountGreaterThanEqual(MAX_ATTEMPTS)).willReturn(3L);

        double value = registry.get("groove.outbox.dlq.size").gauge().value();

        assertThat(value).isEqualTo(3.0);
    }

    @Test
    @DisplayName("recordQuarantined 는 eventType 태그가 붙은 Counter 를 증가시킨다")
    void recordQuarantined_incrementsCounterWithEventTypeTag() {
        metrics.recordQuarantined("ORDER_PAID");
        metrics.recordQuarantined("ORDER_PAID");
        metrics.recordQuarantined("ALBUM_DELETED");

        assertThat(registry.get("groove.outbox.dlq.quarantined").tag("eventType", "ORDER_PAID").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("groove.outbox.dlq.quarantined").tag("eventType", "ALBUM_DELETED").counter().count())
                .isEqualTo(1.0);
    }
}
