package com.groove.catalog.client;

import java.time.Duration;

/** 레이트리밋 대기를 테스트에서 실제로 잠들지 않고 검증할 수 있도록 분리한 함수형 인터페이스. */
@FunctionalInterface
public interface Sleeper {

	void sleep(Duration duration);
}
