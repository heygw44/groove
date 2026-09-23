package com.groove.global.alert;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 같은 경보 키가 window 안에서 중복 전송되지 않게 막고, 억제된 건은 건수·대상으로 모아둔다. 키 종류가 코드에
 * 박힌 고정 집합(십수 개)이라 만료된 항목을 따로 청소하지 않는다. 억제 집계는 인스턴스 메모리에만 쌓이므로
 * 인스턴스가 여러 대면 각자 자기 몫만큼만 요약을 보낸다.
 */
public class AlertThrottle {

	private static final int MAX_SAMPLES = 5;

	private final Map<String, KeyState> states = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration window;

	public AlertThrottle(Clock clock, Duration window) {
		this.clock = clock;
		this.window = window;
	}

	/**
	 * window 안이면 억제하고 카운트만 쌓는다. window 를 지나 통과하면 그 시점을 마지막 전송 시각으로 원자적으로
	 * 기록하고, 그사이 쌓인 억제분이 있으면 {@link ThrottleResult#carried()} 로 함께 돌려준다.
	 */
	public ThrottleResult acquire(Alert alert) {
		long now = clock.millis();
		AtomicReference<ThrottleResult> result = new AtomicReference<>();
		states.compute(alert.key(), (key, state) -> {
			if (state != null && now - state.lastSentMillis() < window.toMillis()) {
				result.set(new ThrottleResult(false, null));
				return state.suppress(alert.severity(), alert.targetId());
			}
			SuppressedSummary carried = state != null && state.suppressedCount() > 0
					? state.toSummary(key, window)
					: null;
			result.set(new ThrottleResult(true, carried));
			return KeyState.startedAt(now);
		});
		return result.get();
	}

	/**
	 * window 가 지났는데도 acquire() 가 다시 호출되지 않아 아직 안 나간 억제 요약을 모아 꺼낸다. 꺼낸 키는
	 * 상태를 초기화하고 이 시점을 새 마지막 전송 시각으로 친다 - 요약 전송도 한 번의 전송이라, 다음 억제 창이
	 * 여기서부터 다시 시작된다.
	 */
	public List<SuppressedSummary> drainExpired() {
		long now = clock.millis();
		List<SuppressedSummary> summaries = new ArrayList<>();
		for (String key : states.keySet()) {
			states.compute(key, (k, state) -> {
				if (state == null || state.suppressedCount() == 0 || now - state.lastSentMillis() < window.toMillis()) {
					return state;
				}
				summaries.add(state.toSummary(k, window));
				return KeyState.startedAt(now);
			});
		}
		return summaries;
	}

	public record ThrottleResult(boolean acquired, SuppressedSummary carried) {
	}

	private record KeyState(long lastSentMillis, int suppressedCount, AlertSeverity highestSeverity,
			List<String> samples) {

		static KeyState startedAt(long now) {
			return new KeyState(now, 0, null, List.of());
		}

		KeyState suppress(AlertSeverity severity, String targetId) {
			AlertSeverity mergedSeverity = higherOf(highestSeverity, severity);
			List<String> mergedSamples = samples;
			if (targetId != null && samples.size() < MAX_SAMPLES && !samples.contains(targetId)) {
				mergedSamples = new ArrayList<>(samples);
				mergedSamples.add(targetId);
			}
			return new KeyState(lastSentMillis, suppressedCount + 1, mergedSeverity, mergedSamples);
		}

		SuppressedSummary toSummary(String key, Duration window) {
			return new SuppressedSummary(key, highestSeverity, suppressedCount, samples, window);
		}

		private static AlertSeverity higherOf(AlertSeverity left, AlertSeverity right) {
			if (left == null) {
				return right;
			}
			if (right == null) {
				return left;
			}
			return left == AlertSeverity.CRITICAL || right == AlertSeverity.CRITICAL ? AlertSeverity.CRITICAL
					: AlertSeverity.WARN;
		}
	}
}
