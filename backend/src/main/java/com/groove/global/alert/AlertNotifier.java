package com.groove.global.alert;

/** 구현은 절대 예외를 던지지 않는다 - 경보 전송 실패가 호출부 로직에 영향을 주면 안 된다. */
public interface AlertNotifier {

	void notify(Alert alert);

	/** 억제 창이 지나도록 나가지 않은 요약을 마저 보낸다. 억제 개념이 없는 구현은 기본 구현(no-op)으로 충분하다. */
	default void flushSuppressed() {
	}
}
