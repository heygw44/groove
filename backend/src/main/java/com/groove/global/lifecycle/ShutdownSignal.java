package com.groove.global.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

/**
 * ContextClosedEvent 는 빈 파괴보다 먼저, DB·Redis 커넥션이 살아 있는 시점에 발행된다.
 * 스케줄러 루프가 이 신호를 보고 다음 건으로 넘어가지 않고 멈추게 하기 위한 용도다.
 */
@Component
public class ShutdownSignal implements ApplicationListener<ContextClosedEvent> {

	private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

	public boolean isShuttingDown() {
		return shuttingDown.get();
	}

	@Override
	public void onApplicationEvent(ContextClosedEvent event) {
		shuttingDown.set(true);
	}
}
