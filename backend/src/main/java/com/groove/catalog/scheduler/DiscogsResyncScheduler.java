package com.groove.catalog.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.config.CatalogFreshnessProperties;
import com.groove.catalog.config.CatalogResyncProperties;
import com.groove.catalog.dto.DiscogsResyncCandidate;
import com.groove.catalog.dto.DiscogsResyncOutcome;
import com.groove.catalog.mapper.DiscogsResyncMapper;
import com.groove.catalog.service.DiscogsResyncLock;
import com.groove.catalog.service.DiscogsResyncService;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.recommend.service.ProductCatalogChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Discogs 릴리즈를 주기적으로 재검증한다. Spring Batch 가 아니라 평범한 스케줄러 + 건별 트랜잭션으로 구성했다 -
 * 후보 선정 쿼리가 discogs_synced_at 오름차순이라 이번에 처리 못한 행이 다음 실행 맨 앞에 자동으로 서므로
 * "재시작 지점 복원"이라는 Batch 의 값어치가 여기서는 무효하다.
 *
 * <p>실패한 행은 discogs_synced_at 이 그대로라 맨 앞에 계속 서서 뒤의 정상 행을 굶긴다. 그래서 실패 시각을
 * 따로 남기고 쿨다운(failureCooldown) 동안 후보에서 뺀다. 레이트리밋은 행 문제가 아니라 남기지 않는다.</p>
 *
 * <p>HTTP 호출({@link PressingLookupClient#getRelease})은 반드시 트랜잭션 밖에서 한다. 레이트리밋 대기와
 * 429 60초 블록을 여기서 흡수해야 그 시간 동안 DB 커넥션을 쥐지 않는다. 적용은 별도 빈({@link DiscogsResyncService})
 * 을 호출해 트랜잭션 하나로 완결한다 - 자기호출로는 {@code @Transactional} 이 걸리지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscogsResyncScheduler {

	static final int SWEEP_MAX_LOOPS = 100;

	private final DiscogsResyncMapper discogsResyncMapper;
	private final DiscogsResyncService discogsResyncService;
	private final DiscogsResyncLock discogsResyncLock;
	private final PressingLookupClient pressingLookupClient;
	private final CatalogResyncProperties properties;
	private final CatalogFreshnessProperties freshnessProperties;
	private final ApplicationEventPublisher eventPublisher;
	private final ShutdownSignal shutdownSignal;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	/** 조회수 우선순위 재검증. 회당 예산(maxCallsPerRun)만큼만 부른다. */
	@Scheduled(fixedDelayString = "${groove.catalog.resync.interval}", initialDelay = 30_000)
	public void resyncPriority() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = discogsResyncLock.runExclusively(this::runPriority);
		if (!acquired) {
			log.info("Discogs 우선순위 재검증 락 획득 실패로 건너뛴다");
		}
	}

	/** 야간 전량 스윕. HIDDEN 포함, MAX_LOOPS 만큼 회당 예산 단위로 반복해 소진한다. */
	@Scheduled(cron = "${groove.catalog.resync.sweep-cron}", zone = "Asia/Seoul")
	public void resyncSweep() {
		if (shutdownSignal.isShuttingDown()) {
			return;
		}
		boolean acquired = discogsResyncLock.runExclusively(this::runSweep);
		if (!acquired) {
			log.info("Discogs 야간 스윕 락 획득 실패로 건너뛴다");
		}
	}

	private void runPriority() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime staleBefore = now.minus(freshnessProperties.ttl());
		LocalDateTime retryBefore = now.minus(properties.failureCooldown());
		LocalDateTime viewSince = now.minus(properties.viewWindow());
		checkBudgetAlert(staleBefore);

		long startedAt = System.currentTimeMillis();
		List<DiscogsResyncCandidate> candidates = discogsResyncMapper.findCandidates(staleBefore, retryBefore,
				viewSince, true, true, properties.maxCallsPerRun());
		ResyncSummary summary = processCandidates(candidates, now);
		logSummary("우선순위 재검증", candidates.size(), summary, System.currentTimeMillis() - startedAt);
		publishChangedEventIfAny(summary);
	}

	private void runSweep() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime staleBefore = now.minus(freshnessProperties.ttl());
		LocalDateTime retryBefore = now.minus(properties.failureCooldown());
		checkBudgetAlert(staleBefore);

		long startedAt = System.currentTimeMillis();
		int candidateTotal = 0;
		int success = 0;
		int failed = 0;
		int changed = 0;
		for (int i = 0; i < SWEEP_MAX_LOOPS; i++) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 야간 스윕 중단 candidates={} success={} failed={}", candidateTotal, success, failed);
				break;
			}
			List<DiscogsResyncCandidate> candidates = discogsResyncMapper.findCandidates(staleBefore, retryBefore,
					null, false, false, properties.maxCallsPerRun());
			if (candidates.isEmpty()) {
				break;
			}
			candidateTotal += candidates.size();
			ResyncSummary summary = processCandidates(candidates, now);
			success += summary.success();
			failed += summary.failed();
			changed += summary.changed();
			// 실패한 행은 쿨다운으로 다음 조회에서 빠지므로 매 바퀴 새 행이 온다. 그런데도 한 바퀴 전부 실패했다면
			// 행 문제가 아니라 Discogs 쪽이 죽은 것이므로(레이트리밋은 쿨다운을 안 남겨 같은 행이 또 온다) 예산을 더
			// 태우지 않고 멈춘다.
			if (summary.success() == 0) {
				log.warn("야간 스윕에서 진전이 없어 중단한다 attempted={} failed={}", candidates.size(), summary.failed());
				break;
			}
			if (candidates.size() < properties.maxCallsPerRun()) {
				break;
			}
		}
		ResyncSummary total = new ResyncSummary(success, failed, changed);
		logSummary("야간 스윕", candidateTotal, total, System.currentTimeMillis() - startedAt);
		publishChangedEventIfAny(total);
	}

	private ResyncSummary processCandidates(List<DiscogsResyncCandidate> candidates, LocalDateTime now) {
		int success = 0;
		int failed = 0;
		int changed = 0;
		for (DiscogsResyncCandidate candidate : candidates) {
			if (shutdownSignal.isShuttingDown()) {
				log.info("셧다운 신호로 Discogs 재검증 중단 processed={} remaining={}", success + failed,
						candidates.size() - (success + failed));
				break;
			}
			try {
				DiscogsReleaseResponse release = pressingLookupClient.getRelease(candidate.discogsReleaseId());
				DiscogsResyncOutcome outcome = discogsResyncService.apply(candidate.productId(),
						candidate.discogsReleaseId(), release);
				success++;
				if (outcome.changed()) {
					changed++;
				}
			} catch (BusinessException e) {
				failed++;
				if (e.getErrorCode() == ErrorCode.CATALOG_RELEASE_NOT_FOUND) {
					markReleaseNotFoundSafely(candidate);
				} else {
					log.warn("Discogs 재검증 실패 productId={} discogsReleaseId={}", candidate.productId(),
							candidate.discogsReleaseId(), e);
					// 레이트리밋은 Discogs 전체 신호라 행에 남기면 멀쩡한 행까지 쿨다운에 걸린다.
					if (e.getErrorCode() != ErrorCode.CATALOG_RATE_LIMITED) {
						recordFailureSafely(candidate, now);
					}
				}
			} catch (RuntimeException e) {
				failed++;
				log.warn("Discogs 재검증 실패 productId={} discogsReleaseId={}", candidate.productId(),
						candidate.discogsReleaseId(), e);
				recordFailureSafely(candidate, now);
			}
		}
		return new ResyncSummary(success, failed, changed);
	}

	// 후처리 쓰기가 실패해도 한 행 때문에 페이지 전체가 멈추면 안 되므로 여기서 삼킨다.
	private void markReleaseNotFoundSafely(DiscogsResyncCandidate candidate) {
		try {
			discogsResyncService.markReleaseNotFound(candidate.productId(), candidate.discogsReleaseId());
		} catch (RuntimeException e) {
			log.warn("Discogs 릴리즈 참조 해제 실패 productId={} discogsReleaseId={}", candidate.productId(),
					candidate.discogsReleaseId(), e);
		}
	}

	// 기록에 실패하면 이 행은 다음 실행에도 맨 앞에 서지만, 그것 때문에 남은 행 처리를 멈추지는 않는다.
	private void recordFailureSafely(DiscogsResyncCandidate candidate, LocalDateTime now) {
		try {
			discogsResyncService.markResyncFailed(candidate.productId(), now);
		} catch (RuntimeException e) {
			log.warn("Discogs 재검증 실패 기록 실패 productId={} discogsReleaseId={}", candidate.productId(),
					candidate.discogsReleaseId(), e);
		}
	}

	// 건별 발행은 ProductFeatureCache 를 계속 버리게 되므로 실행 끝에 변경 건수가 있을 때 한 번만 발행한다.
	private void publishChangedEventIfAny(ResyncSummary summary) {
		if (summary.changed() > 0) {
			eventPublisher.publishEvent(new ProductCatalogChangedEvent());
		}
	}

	// 임계를 상수로 박지 않고 설정값(예산·주기·TTL)에서 매번 다시 계산한다.
	private void checkBudgetAlert(LocalDateTime staleBefore) {
		long totalCandidates = discogsResyncMapper.countStale(staleBefore);
		long intervalSeconds = Math.max(1, properties.interval().getSeconds());
		double ratePerSecond = (double) properties.maxCallsPerRun() / intervalSeconds;
		double estimatedSeconds = totalCandidates / ratePerSecond;
		long ttlSeconds = freshnessProperties.ttl().getSeconds();
		if (estimatedSeconds > ttlSeconds) {
			long estimatedMinutes = Math.round(estimatedSeconds / 60.0);
			long ttlMinutes = ttlSeconds / 60;
			log.error("재검증 주기가 신선도 TTL 을 초과한다 candidates={} estimatedMinutes={} ttlMinutes={}",
					totalCandidates, estimatedMinutes, ttlMinutes);
			alertNotifier.notify(Alert.warn("catalog.resync-budget-exceeded",
					"재검증 주기가 신선도 TTL 을 초과한다 candidates=" + totalCandidates + " estimatedMinutes=" + estimatedMinutes
							+ " ttlMinutes=" + ttlMinutes,
					null));
		}
	}

	private void logSummary(String label, int candidateCount, ResyncSummary summary, long elapsedMs) {
		log.info("{} 완료 candidates={} success={} failed={} changed={} elapsedMs={}", label, candidateCount,
				summary.success(), summary.failed(), summary.changed(), elapsedMs);
	}

	private record ResyncSummary(int success, int failed, int changed) {
	}
}
