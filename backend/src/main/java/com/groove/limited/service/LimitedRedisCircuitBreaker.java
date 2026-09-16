package com.groove.limited.service;

import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.groove.limited.config.LimitedCircuitProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 한정반 Redis 장애 서킷. 상태 3개(CLOSED/OPEN/HALF_OPEN)와 실패 카운터 하나, OPEN 진입 시각 하나로 판단한다.
 * 실패는 {@link org.springframework.dao.DataAccessException}(연결 실패·타임아웃)만 센다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitedRedisCircuitBreaker {

	public enum State {
		CLOSED, OPEN, HALF_OPEN
	}

	private final LimitedCircuitProperties circuitProperties;
	private final Clock clock;

	private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
	private final AtomicInteger failures = new AtomicInteger();
	private final AtomicLong openedAtMillis = new AtomicLong();
	private final AtomicBoolean probeTaken = new AtomicBoolean(false);
	private final Set<Long> fallbackDrops = ConcurrentHashMap.newKeySet();

	/** OPEN 이고 openDuration 이 지났으면 HALF_OPEN 으로 보고한다. */
	public State state() {
		if (state.get() == State.OPEN && openDurationElapsed()) {
			return State.HALF_OPEN;
		}
		return state.get();
	}

	/** 부가 호출(recordAttempt 등) 판단용. 프로브를 소모하지 않는다. */
	public boolean isClosed() {
		return state() == State.CLOSED;
	}

	/** afterCommit 등 부가 호출을 건너뛸지 판단용. OPEN 이고 openDuration 이 안 지났을 때만 true. */
	public boolean isOpen() {
		return state() == State.OPEN;
	}

	/** CLOSED 는 항상 true, OPEN 은 항상 false, HALF_OPEN 은 첫 한 스레드만 true(프로브)다. */
	public boolean allowRedis() {
		State current = state();
		if (current == State.CLOSED) {
			return true;
		}
		if (current == State.OPEN) {
			return false;
		}
		return probeTaken.compareAndSet(false, true);
	}

	public synchronized void onSuccess() {
		boolean wasHalfOpen = state() == State.HALF_OPEN;
		failures.set(0);
		if (wasHalfOpen) {
			state.set(State.CLOSED);
			probeTaken.set(false);
			log.info("한정반 Redis 서킷 CLOSED 복귀");
		}
	}

	public synchronized void onFailure() {
		if (state() == State.HALF_OPEN) {
			open();
			return;
		}
		if (failures.incrementAndGet() >= circuitProperties.failureThreshold()) {
			open();
		}
	}

	private void open() {
		if (state() == State.OPEN) {
			return;
		}
		state.set(State.OPEN);
		openedAtMillis.set(clock.millis());
		probeTaken.set(false);
		log.error("한정반 Redis 서킷 OPEN, DB 폴백 전환");
	}

	private boolean openDurationElapsed() {
		long elapsed = clock.millis() - openedAtMillis.get();
		return elapsed >= circuitProperties.openDuration().toMillis();
	}

	/** 폴백으로 판 드롭을 기록한다. 서킷 복귀 시 재적재 대상으로 쓰인다. */
	public void noteFallback(Long dropId) {
		fallbackDrops.add(dropId);
	}

	/** 복귀 시 재적재 대상 스냅샷. */
	public Set<Long> fallbackDrops() {
		return Set.copyOf(fallbackDrops);
	}

	public void clearFallbackDrops(Collection<Long> dropIds) {
		fallbackDrops.removeAll(dropIds);
	}
}
