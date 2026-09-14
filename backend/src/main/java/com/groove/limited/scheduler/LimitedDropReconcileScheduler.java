package com.groove.limited.scheduler;

import static com.groove.limited.entity.LimitedDropStatus.OPEN;
import static com.groove.limited.entity.LimitedDropStatus.SOLD_OUT;

import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropSyncService;
import com.groove.limited.service.LimitedReconcileLock;
import com.groove.limited.service.LimitedSyncResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 한정반 Redis 선점 누수를 주기적으로 DB 기준으로 대사한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitedDropReconcileScheduler {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedDropSyncService limitedDropSyncService;
	private final LimitedReconcileLock reconcileLock;
	private final ShutdownSignal shutdownSignal;

	@Scheduled(fixedDelayString = "${groove.limited.reconcile.interval}", initialDelay = 30_000)
	public void reconcile() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = reconcileLock.runExclusively(this::runReconcile);
		if (!acquired) {
			log.info("한정반 대사 락 획득 실패로 건너뛴다");
		}
	}

	private void runReconcile() {
		List<Long> dropIds = limitedDropRepository.findIdsByStatusIn(List.of(OPEN, SOLD_OUT));
		int processed = 0;
		int corrected = 0;
		int failed = 0;
		for (Long dropId : dropIds) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 한정반 대사 중단 processed={} remaining={}", processed, dropIds.size() - processed);
				break;
			}
			try {
				Optional<LimitedSyncResult> result = limitedDropSyncService.sync(dropId);
				if (result.isPresent() && result.get().changed()) {
					corrected++;
				}
			} catch (RuntimeException e) {
				failed++;
				log.warn("한정반 대사 처리 실패 dropId={}", dropId, e);
			}
			processed++;
		}
		if (corrected > 0 || failed > 0) {
			log.info("한정반 대사 완료 drops={} corrected={} failed={}", dropIds.size(), corrected, failed);
		} else {
			log.debug("한정반 대사 완료 drops={} corrected={} failed={}", dropIds.size(), corrected, failed);
		}
	}
}
