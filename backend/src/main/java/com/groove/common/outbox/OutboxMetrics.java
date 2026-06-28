package com.groove.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 DLQ(격리) 운영 메트릭. groove.outbox.dlq.size(Gauge) 는 현재 격리 backlog 를 scrape 시점에
 * 조회하는 권위 값, groove.outbox.dlq.quarantined(Counter, eventType 태그) 는 격리 전이 누적 건수다.
 */
@Component
public class OutboxMetrics {

    private final OutboxEventRepository repository;
    private final MeterRegistry registry;
    private final int maxAttempts;

    public OutboxMetrics(MeterRegistry registry,
                         OutboxEventRepository repository,
                         @Value("${groove.outbox.relay.max-attempts:5}") int maxAttempts) {
        this.registry = registry;
        this.repository = repository;
        this.maxAttempts = maxAttempts;
        // Gauge supplier 는 scrape 1회당 1회만 평가. DB 장애로 던지면 Micrometer 가 NaN(absent) 처리하므로
        // 알림룰은 absent() 를 0 과 구분해야 한다.
        registry.gauge("groove.outbox.dlq.size", this,
                m -> m.repository.countByPublishedAtIsNullAndAttemptCountGreaterThanEqual(m.maxAttempts));
    }

    /**
     * 이벤트가 재시도 상한에 도달해 DLQ 로 격리되는 전이 시점에 1회 호출한다. eventType 별 누적 격리 건수를 센다.
     * eventType 은 부팅 시 핸들러로 주입되는 bounded 집합이라 태그 카디널리티가 안전하다.
     */
    public void recordQuarantined(String eventType) {
        Counter.builder("groove.outbox.dlq.quarantined")
                .tag("eventType", eventType)
                .register(registry)
                .increment();
    }
}
