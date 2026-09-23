package com.groove.global.config;

import org.springframework.stereotype.Component;

import io.lettuce.core.event.Event;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent;
import io.lettuce.core.event.connection.ReconnectAttemptEvent;
import io.lettuce.core.event.connection.ReconnectFailedEvent;
import io.lettuce.core.resource.ClientResources;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/** 운영에서도 Redis 연결 끊김·재연결 이력을 남기려고 상시로 구독을 둔다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConnectionEventLogger {

	private final ClientResources clientResources;

	private Disposable subscription;

	@PostConstruct
	public void init() {
		subscription = clientResources.eventBus().get().subscribe(this::onEvent);
	}

	@PreDestroy
	public void destroy() {
		if (subscription != null) {
			subscription.dispose();
		}
	}

	private void onEvent(Event event) {
		if (event instanceof ConnectionActivatedEvent activated) {
			log.info("Redis 연결 활성화 remoteAddress={}", activated.remoteAddress());
		} else if (event instanceof ConnectionDeactivatedEvent deactivated) {
			log.info("Redis 연결 종료 remoteAddress={}", deactivated.remoteAddress());
		} else if (event instanceof ReconnectFailedEvent failed) {
			log.info("Redis 재연결 실패 attempt={} remoteAddress={} cause={}", failed.getAttempt(), failed.remoteAddress(),
					failed.getCause() == null ? null : failed.getCause().getMessage());
		} else if (event instanceof ReconnectAttemptEvent attempt) {
			log.debug("Redis 재연결 시도 attempt={} remoteAddress={}", attempt.getAttempt(), attempt.remoteAddress());
		}
	}
}
