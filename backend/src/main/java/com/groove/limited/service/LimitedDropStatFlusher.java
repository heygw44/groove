package com.groove.limited.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStat;
import com.groove.limited.repository.LimitedDropStatRepository;

import lombok.RequiredArgsConstructor;

/** 집계 Hash 를 limited_drop_stat 으로 옮기고 Redis 키를 정리한다. 호출자 트랜잭션에 참여한다. */
@Service
@RequiredArgsConstructor
public class LimitedDropStatFlusher {

	private final LimitedDropStatRepository limitedDropStatRepository;
	private final LimitedDropRedisService limitedDropRedisService;
	private final Clock clock;

	/**
	 * Redis 클리어 직전 커밋 중 프로세스가 죽으면 키는 지웠는데 DB 는 커밋 전인 창이 남는다.
	 * 집계는 부가 데이터라 그 손실은 감수한다.
	 */
	@Transactional
	public void flushAndClear(LimitedDrop drop) {
		// 여기서 나는 예외는 잡지 않는다. 삼키면 clear() 가 실제 데이터를 지워 복구 경로가 사라진다.
		Map<LimitedAttemptResult, Long> counts = limitedDropRedisService.getAttemptsForFlush(drop.getId());
		if (!counts.isEmpty()) {
			LocalDateTime flushedAt = LocalDateTime.now(clock);
			Optional<LimitedDropStat> existing = limitedDropStatRepository.findByDropId(drop.getId());
			if (existing.isPresent()) {
				existing.get().apply(counts, flushedAt);
			} else {
				limitedDropStatRepository.save(LimitedDropStat.of(drop, counts, flushedAt));
			}
		}
		limitedDropRedisService.clear(drop.getId());
	}
}
