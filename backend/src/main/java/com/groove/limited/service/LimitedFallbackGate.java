package com.groove.limited.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;

import com.groove.limited.config.LimitedCircuitProperties;

import lombok.RequiredArgsConstructor;

/** Redis 서킷 OPEN 중 DB 락 경로로 들어가는 요청 수를 드롭당 제한한다. */
@Component
@RequiredArgsConstructor
public class LimitedFallbackGate {

	private final LimitedCircuitProperties circuitProperties;

	private final ConcurrentHashMap<Long, Semaphore> semaphores = new ConcurrentHashMap<>();

	/** 허가를 얻으면 true. 드롭당 permit 이 소진됐으면 false(호출부가 LIMITED_BUSY 로 거절한다). */
	public boolean tryEnter(Long dropId) {
		return semaphoreFor(dropId).tryAcquire();
	}

	public void exit(Long dropId) {
		semaphoreFor(dropId).release();
	}

	private Semaphore semaphoreFor(Long dropId) {
		return semaphores.computeIfAbsent(dropId, id -> new Semaphore(circuitProperties.fallbackPermits()));
	}
}
