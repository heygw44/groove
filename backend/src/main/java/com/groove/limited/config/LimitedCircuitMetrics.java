package com.groove.limited.config;

import org.springframework.stereotype.Component;

import com.groove.limited.service.LimitedRedisCircuitBreaker;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;

/** 한정반 Redis 서킷 상태를 게이지로 내보낸다. */
@Component
@RequiredArgsConstructor
public class LimitedCircuitMetrics implements MeterBinder {

	private final LimitedRedisCircuitBreaker circuitBreaker;

	@Override
	public void bindTo(MeterRegistry registry) {
		// 게이지는 대상 객체를 약참조로 쥐어 싱글턴 빈을 넘긴다. ordinal 대신 switch 로 순서 변경에 안전하게 대응한다.
		Gauge.builder("groove.limited.circuit.state", circuitBreaker, LimitedCircuitMetrics::stateValue)
			.description("한정반 Redis 서킷 상태 0=CLOSED 1=OPEN 2=HALF_OPEN")
			.register(registry);
	}

	private static int stateValue(LimitedRedisCircuitBreaker circuitBreaker) {
		return switch (circuitBreaker.state()) {
			case CLOSED -> 0;
			case OPEN -> 1;
			case HALF_OPEN -> 2;
		};
	}
}
