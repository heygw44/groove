package com.groove.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 DLQ(격리) 운영 메트릭 (#323) — 영구 실패(poison) 이벤트의 가시성을 actuator/Prometheus 로 노출한다.
 * 두 지표를 상호 보완으로 내보낸다:
 * - groove.outbox.dlq.size (Gauge) — 현재 격리 backlog. 미발행 + attempt_count >= max-attempts 행 수를 scrape
 *   시점에 조회한다(인덱스 카운트). 재시작에도 DB 진실값을 반영하는 권위 값이다.
 * - groove.outbox.dlq.quarantined (Counter, eventType 태그) — 격리 전이 누적 건수. 상한 도달 순간 1회 증가하며
 *   increase()[5m] 류 알림룰로 신규 격리 발생률을 잡는다.
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
        // Gauge 는 scrape 시점에만 supplier 를 평가한다 — 상시 쿼리가 아니라 scrape 1회당 인덱스 카운트 1회.
        // supplier 가 던지면(DB 장애) Micrometer(DefaultGauge.value)가 해당 gauge 만 NaN 처리하고 엔드포인트는
        // 정상 200 이다. 단 값 0 이 아니라 부재(absent)가 되므로 알림룰은 absent() 를 0 과 구분해야 한다.
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
