package com.groove.catalog.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * Discogs 레이트리밋(인증 60/min) 을 지키기 위한 토큰 버킷. 여러 요청 스레드가 하나의 인스턴스를
 * 공유하므로 {@code synchronized} 로 직렬화한다.
 */
@Component
public class DiscogsRateLimiter {

	private static final int CAPACITY = 60;
	private static final double TOKENS_PER_SECOND = 1.0;

	private final Clock clock;
	private final Sleeper sleeper;

	private double tokens;
	private Instant lastRefilledAt;
	private Instant blockedUntil;

	public DiscogsRateLimiter(Clock clock, Sleeper sleeper) {
		this.clock = clock;
		this.sleeper = sleeper;
		this.tokens = CAPACITY;
		this.lastRefilledAt = clock.instant();
		this.blockedUntil = Instant.MIN;
	}

	/** 429 이후 대기 중이면 그때까지, 아니면 토큰이 채워질 때까지 기다렸다가 1개를 소비한다. */
	public synchronized void acquire() {
		Instant now = clock.instant();
		if (blockedUntil.isAfter(now)) {
			sleeper.sleep(Duration.between(now, blockedUntil));
			now = clock.instant();
		}
		refill(now);
		if (tokens < 1) {
			Duration wait = Duration.ofMillis(Math.round((1 - tokens) / TOKENS_PER_SECOND * 1000));
			sleeper.sleep(wait);
			refill(clock.instant());
		}
		tokens -= 1;
	}

	/** 응답 헤더의 남은 호출 수로 버킷을 낮춘다. 실제 잔여량이 더 크면 무시한다. */
	public synchronized void updateRemaining(int remaining) {
		tokens = Math.min(tokens, remaining);
	}

	/** 429 응답의 Retry-After 초 동안 다음 acquire 를 막는다. */
	public synchronized void blockFor(long seconds) {
		blockedUntil = clock.instant().plusSeconds(seconds);
	}

	private void refill(Instant now) {
		double elapsedSeconds = Duration.between(lastRefilledAt, now).toMillis() / 1000.0;
		if (elapsedSeconds > 0) {
			tokens = Math.min(CAPACITY, tokens + elapsedSeconds * TOKENS_PER_SECOND);
			lastRefilledAt = now;
		}
	}
}
