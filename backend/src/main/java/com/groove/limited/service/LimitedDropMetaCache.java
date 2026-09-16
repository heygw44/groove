package com.groove.limited.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.groove.limited.config.LimitedProperties;
import com.groove.limited.repository.LimitedDropRepository;

import lombok.RequiredArgsConstructor;

/**
 * 구매 진입점이 매번 {@code findById} 로 드롭 상태(status/openAt/closeAt)를 읽던 것을 TTL 동안 로컬에
 * 캐싱한다. 당첨자가 행 락을 오래 쥐고 있는 동안 탈락자들까지 커넥션을 못 잡아 밀리는 문제를 없애려면
 * Redis 선점 전 단계에서는 DB 를 아예 타지 않아야 한다. 단일 인스턴스 배포라 로컬 캐시로 충분하고, 스케줄러의
 * open/close 주기(10초)보다 훨씬 짧게 잡아 지연 체감을 줄인다.
 */
@Component
@RequiredArgsConstructor
public class LimitedDropMetaCache {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedProperties limitedProperties;
	private final Clock clock;

	private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
	private final AtomicLong loadCount = new AtomicLong();

	public Optional<LimitedDropMeta> get(Long dropId) {
		Entry entry = cache.get(dropId);
		if (isFresh(entry)) {
			return Optional.of(entry.meta());
		}
		return load(dropId);
	}

	/**
	 * 트랜잭션이 활성이면 커밋 뒤에 지운다 — 커밋 전에 지우면 그 사이 다른 요청이 아직 안 바뀐 DB 값을
	 * 다시 캐시에 채워 넣는 창이 생긴다. 트랜잭션이 없으면 즉시 지운다.
	 */
	public void evict(Long dropId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			cache.remove(dropId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				cache.remove(dropId);
			}
		});
	}

	/** 테스트용 적재 횟수. */
	public long loadCount() {
		return loadCount.get();
	}

	private Optional<LimitedDropMeta> load(Long dropId) {
		Optional<LimitedDropMeta> loaded = limitedDropRepository.findById(dropId).map(LimitedDropMeta::from);
		loadCount.incrementAndGet();
		loaded.ifPresent(meta -> cache.put(dropId, new Entry(meta, clock.instant())));
		return loaded;
	}

	private boolean isFresh(Entry entry) {
		if (entry == null) {
			return false;
		}
		Duration ttl = limitedProperties.metaCacheTtl();
		if (ttl.isZero() || ttl.isNegative()) {
			return false;
		}
		return entry.loadedAt().plus(ttl).isAfter(clock.instant());
	}

	private record Entry(LimitedDropMeta meta, Instant loadedAt) {
	}
}
