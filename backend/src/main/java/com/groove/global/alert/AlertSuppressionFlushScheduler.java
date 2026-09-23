package com.groove.global.alert;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 억제 창이 지나도록 다시 나가지 않은 경보 요약을 주기적으로 밀어낸다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertSuppressionFlushScheduler {

	private final AlertNotifier alertNotifier;

	@Scheduled(fixedDelayString = "${groove.alert.flush-interval:60s}", initialDelay = 60_000)
	public void flush() {
		try {
			alertNotifier.flushSuppressed();
		} catch (RuntimeException e) {
			log.warn("억제 경보 요약 전송 중 예외 발생", e);
		}
	}
}
