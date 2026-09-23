package com.groove.global.alert;

import java.time.Duration;
import java.util.List;

/** 억제 창 안에서 쌓인 경보를 건수와 대상 표본으로 요약한다. */
public record SuppressedSummary(String key, AlertSeverity severity, int count, List<String> samples,
		Duration window) {

	public String describe() {
		StringBuilder text = new StringBuilder();
		text.append("직전 ").append(window.toMinutes()).append("분 같은 경보 ").append(count).append("건 억제");
		if (!samples.isEmpty()) {
			text.append(", 대상: ").append(String.join(", ", samples));
			int remaining = count - samples.size();
			if (remaining > 0) {
				text.append(" 외 ").append(remaining).append("건");
			}
		}
		return text.toString();
	}
}
