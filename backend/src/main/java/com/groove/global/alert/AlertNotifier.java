package com.groove.global.alert;

/** 구현은 절대 예외를 던지지 않는다 - 경보 전송 실패가 호출부 로직에 영향을 주면 안 된다. */
public interface AlertNotifier {

	void notify(Alert alert);
}
