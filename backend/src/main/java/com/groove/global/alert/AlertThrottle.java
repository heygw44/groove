package com.groove.global.alert;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 같은 경보 키가 window 안에서 중복 전송되지 않게 막는다. 키 종류가 코드에 박힌 고정 집합(십수 개)이라
 * 만료된 항목을 따로 청소하지 않는다.
 */
public class AlertThrottle {

	private final Map<String, Long> lastSentMillis = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration window;

	public AlertThrottle(Clock clock, Duration window) {
		this.clock = clock;
		this.window = window;
	}

	/** window 안에 이미 보낸 키면 false. 통과하면 그 시점을 마지막 전송 시각으로 원자적으로 기록한다. */
	public boolean tryAcquire(String key) {
		long now = clock.millis();
		AtomicBoolean acquired = new AtomicBoolean(false);
		lastSentMillis.compute(key, (k, lastSent) -> {
			if (lastSent != null && now - lastSent < window.toMillis()) {
				return lastSent;
			}
			acquired.set(true);
			return now;
		});
		return acquired.get();
	}
}
