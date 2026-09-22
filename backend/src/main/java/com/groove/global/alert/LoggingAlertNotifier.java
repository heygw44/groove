package com.groove.global.alert;

import lombok.extern.slf4j.Slf4j;

/** 웹훅 URL 미설정 시 주입되는 기본 구현(로컬·테스트). 호출부가 이미 log.error 를 남기므로 info 면 충분하다. */
@Slf4j
public class LoggingAlertNotifier implements AlertNotifier {

	@Override
	public void notify(Alert alert) {
		log.info("경보 채널 미설정, 로그로만 남긴다 severity={} key={} summary={} targetId={}", alert.severity(), alert.key(),
				alert.summary(), alert.targetId());
	}
}
