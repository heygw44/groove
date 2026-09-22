package com.groove.global.alert;

/** {@code key} 는 {도메인}.{원인-kebab} 형식(예 limited.release-failed)이고 억제 판단은 이 값만 본다. */
public record Alert(AlertSeverity severity, String key, String summary, String targetId) {

	public Alert {
		if (severity == null) {
			throw new IllegalArgumentException("severity 는 필수입니다.");
		}
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("key 는 비어 있을 수 없습니다.");
		}
		if (summary == null || summary.isBlank()) {
			throw new IllegalArgumentException("summary 는 비어 있을 수 없습니다.");
		}
	}

	public static Alert warn(String key, String summary, String targetId) {
		return new Alert(AlertSeverity.WARN, key, summary, targetId);
	}

	public static Alert critical(String key, String summary, String targetId) {
		return new Alert(AlertSeverity.CRITICAL, key, summary, targetId);
	}
}
