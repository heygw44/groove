package com.groove.limited.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.limited.config.LimitedReconcileProperties;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Redis stock/buyers/pending 을 DB 기준으로 맞춘다. 대사 스케줄러·키 유실 재적재·서킷 복귀가 모두 이 한 경로를 쓴다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimitedDropSyncService {

	private final LimitedDropRepository limitedDropRepository;
	private final LimitedPurchaseRepository limitedPurchaseRepository;
	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedReconcileProperties reconcileProperties;
	private final Clock clock;

	/** 드롭이 없거나 OPEN/SOLD_OUT 이 아니면 Redis 를 건드리지 않고 empty 를 반환한다. */
	@Transactional
	public Optional<LimitedSyncResult> sync(Long dropId) {
		Optional<LimitedDrop> found = limitedDropRepository.findByIdForUpdate(dropId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		LimitedDrop drop = found.get();
		if (drop.getStatus() != LimitedDropStatus.OPEN && drop.getStatus() != LimitedDropStatus.SOLD_OUT) {
			return Optional.empty();
		}

		int dbRemaining = drop.remainingQuantity();
		List<Long> memberIds = limitedPurchaseRepository.findMemberIdsByDropId(dropId);
		long cutoff = clock.millis() - reconcileProperties.grace().toMillis();

		LimitedSyncResult result = limitedDropRedisService.sync(dropId, dbRemaining, memberIds, cutoff);
		if (result.changed()) {
			log.warn("한정반 Redis 대사 보정 dropId={} stock {}→{} buyersChanged={} leaked={} cleared={}", dropId,
					result.stockBefore(), result.stockAfter(), result.buyersChanged(), result.leaked(),
					result.cleared());
		} else {
			log.debug("한정반 Redis 대사 변화 없음 dropId={}", dropId);
		}
		return Optional.of(result);
	}

	/** 키 유실 시 드롭당 한 요청만 재적재한다. 락을 못 잡으면 false. */
	@Transactional
	public boolean rebuildOnce(Long dropId) {
		if (!limitedDropRedisService.tryLockRebuild(dropId)) {
			return false;
		}
		sync(dropId);
		limitedDropRedisService.unlockRebuild(dropId);
		log.info("한정반 Redis 재고 키 재적재 dropId={}", dropId);
		return true;
	}
}
